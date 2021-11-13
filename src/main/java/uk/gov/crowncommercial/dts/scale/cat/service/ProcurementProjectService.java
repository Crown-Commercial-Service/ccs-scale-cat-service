package uk.gov.crowncommercial.dts.scale.cat.service;

import static java.time.Duration.ofSeconds;
import static java.util.Optional.ofNullable;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig.ENDPOINT;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.web.reactive.function.client.WebClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.config.AgreementsServiceAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.exception.AuthorisationFailureException;
import uk.gov.crowncommercial.dts.scale.cat.exception.JaggaerApplicationException;
import uk.gov.crowncommercial.dts.scale.cat.exception.ResourceNotFoundException;
import uk.gov.crowncommercial.dts.scale.cat.exception.UnhandledEdgeCaseException;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.ProjectEventType;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementProject;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.*;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.*;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.Tender;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;
import uk.gov.crowncommercial.dts.scale.cat.utils.TendersAPIModelUtils;

/**
 * Procurement projects service layer. Handles interactions with Jaggaer and the persistence layer
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProcurementProjectService {

  private final JaggaerAPIConfig jaggaerAPIConfig;
  private final WebClient jaggaerWebClient;
  private final UserProfileService userProfileService;
  private final ConclaveService conclaveService;
  private final RetryableTendersDBDelegate retryableTendersDBDelegate;
  private final ProcurementEventService procurementEventService;
  private final TendersAPIModelUtils tendersAPIModelUtils;
  private final AgreementsServiceAPIConfig agreementsServiceAPIConfig;
  private final WebClient agreementsServiceWebClient;
  private final ModelMapper modelMapper;

  /**
   * SCC-440/441
   * <p>
   * Create a default project and event in Jaggaer from CA and Lot number.
   * <p>
   * Specifically, for the current user principal, performs the following general steps:
   * <ol>
   * <li>Resolve the user principal to a Jaggaer user ID
   * <li>Invoke the Jaggaer API create project endpoint (specifying a template)
   * <li>Handle any application error returned from Jaggaer
   * <li>Persist details of the procurement project to the database
   * <li>Invoke {@link ProcurementEventService#createFromAgreementDetails(Integer, String)}} to
   * create the corresponding default event
   * <li>Return the details
   * </ol>
   *
   *
   * @param agreementDetails
   * @param principal
   * @return draft procurement project
   */
  public DraftProcurementProject createFromAgreementDetails(final AgreementDetails agreementDetails,
      final String principal) {

    // Fetch Jaggaer ID and Buyer company ID from Jaggaer profile based on OIDC login id
    var jaggaerUserId = userProfileService.resolveBuyerUserByEmail(principal)
        .orElseThrow(() -> new AuthorisationFailureException("Jaggaer user not found")).getUserId();
    var jaggaerBuyerCompanyId =
        userProfileService.resolveBuyerCompanyByEmail(principal).getBravoId();
    var projectTitle = getDefaultProjectTitle(agreementDetails, "CCS");

    var tender = Tender.builder().title(projectTitle)
        .buyerCompany(BuyerCompany.builder().id(jaggaerBuyerCompanyId).build())
        .projectOwner(ProjectOwner.builder().id(jaggaerUserId).build())
        .sourceTemplateReferenceCode(jaggaerAPIConfig.getCreateProject().get("templateId")).build();

    var projectBuilder = Project.builder().tender(tender);

    // By default, adding the division is disabled
    if (Boolean.TRUE.equals(jaggaerAPIConfig.getAddDivisionToProjectTeam())) {
      var userDivision = User.builder().code("DIVISION").build();
      var projectTeam = ProjectTeam.builder().user(Collections.singleton(userDivision)).build();
      projectBuilder.projectTeam(projectTeam);
    }
    log.debug("Project to create: {}", projectBuilder.toString());

    var createUpdateProject =
        new CreateUpdateProject(OperationCode.CREATE_FROM_TEMPLATE, projectBuilder.build());

    var createProjectResponse =
        ofNullable(jaggaerWebClient.post().uri(jaggaerAPIConfig.getCreateProject().get(ENDPOINT))
            .bodyValue(createUpdateProject).retrieve().bodyToMono(CreateUpdateProjectResponse.class)
            .block(Duration.ofSeconds(jaggaerAPIConfig.getTimeoutDuration())))
                .orElseThrow(() -> new JaggaerApplicationException(INTERNAL_SERVER_ERROR.value(),
                    "Unexpected error creating project"));

    if (createProjectResponse.getReturnCode() != 0
        || !"OK".equals(createProjectResponse.getReturnMessage())) {
      throw new JaggaerApplicationException(createProjectResponse.getReturnCode(),
          createProjectResponse.getReturnMessage());
    }
    log.info("Created project: {}", createProjectResponse);

    var procurementProject = retryableTendersDBDelegate
        .save(ProcurementProject.of(agreementDetails, createProjectResponse.getTenderCode(),
            createProjectResponse.getTenderReferenceCode(), projectTitle, principal));

    var eventSummary = procurementEventService.createEvent(procurementProject.getId(),
        new CreateEvent(), null, principal);

    return tendersAPIModelUtils.buildDraftProcurementProject(agreementDetails,
        procurementProject.getId(), eventSummary.getId(), projectTitle);
  }

  String getDefaultProjectTitle(final AgreementDetails agreementDetails,
      final String organisation) {
    return String.format(jaggaerAPIConfig.getCreateProject().get("defaultTitleFormat"),
        agreementDetails.getAgreementId(), agreementDetails.getLotId(), organisation);
  }

  /**
   * Update project name.
   *
   * @param projectId The Project Id
   * @param projectName The Project Name
   * @param principal The Principal
   */
  public void updateProcurementProjectName(final Integer projectId, final String projectName,
      final String principal) {

    Assert.hasLength(projectName, "New project name must be supplied");

    var project = retryableTendersDBDelegate.findProcurementProjectById(projectId)
        .orElseThrow(() -> new ResourceNotFoundException("Project '" + projectId + "' not found"));
    var tender = Tender.builder().tenderCode(project.getExternalProjectId())
        .tenderReferenceCode(project.getExternalReferenceId()).title(projectName).build();
    var updateProject =
        new CreateUpdateProject(OperationCode.UPDATE, Project.builder().tender(tender).build());

    var updateProjectResponse =
        ofNullable(jaggaerWebClient.post().uri(jaggaerAPIConfig.getCreateProject().get(ENDPOINT))
            .bodyValue(updateProject).retrieve().bodyToMono(CreateUpdateProjectResponse.class)
            .block(Duration.ofSeconds(jaggaerAPIConfig.getTimeoutDuration())))
                .orElseThrow(() -> new JaggaerApplicationException(INTERNAL_SERVER_ERROR.value(),
                    "Unexpected error updating project"));

    if (updateProjectResponse.getReturnCode() != 0
        || !"OK".equals(updateProjectResponse.getReturnMessage())) {
      throw new JaggaerApplicationException(updateProjectResponse.getReturnCode(),
          updateProjectResponse.getReturnMessage());
    }
    log.info("Updated project: {}", updateProjectResponse);

    project.setProjectName(projectName);
    project.setUpdatedAt(Instant.now());
    project.setUpdatedBy(principal);
    retryableTendersDBDelegate.save(project);

  }

  /**
   * Event types for given project
   *
   * @param projectId the project id
   * @return Collection of event types
   */
  public Collection<EventType> getProjectEventTypes(final Integer projectId) {

    final var project = retryableTendersDBDelegate.findProcurementProjectById(projectId)
        .orElseThrow(() -> new ResourceNotFoundException("Project '" + projectId + "' not found"));

    final var eventTypes = ofNullable(agreementsServiceWebClient.get()
        .uri(agreementsServiceAPIConfig.getGetEventTypesForAgreement().get("uriTemplate"),
            project.getCaNumber(), project.getLotNumber())
        .retrieve().bodyToMono(ProjectEventType[].class)
        .block(Duration.ofSeconds(agreementsServiceAPIConfig.getTimeoutDuration()))).orElseThrow(
            () -> new ResourceNotFoundException("Unexpected error finding event types"));

    return Arrays.stream(eventTypes).map(object -> modelMapper.map(object, EventType.class))
        .collect(Collectors.toList());
  }

  /**
   * Get Project Team members. This is a combination of Project Team members on the project and
   * email recipients on the rfx.
   *
   * @param projectId CCS project id
   * @return Collection of project team members
   */
  public Collection<TeamMember> getProjectTeamMembers(final Integer projectId) {

    // Get Project (project team)
    var dbProject = retryableTendersDBDelegate.findProcurementProjectById(projectId)
        .orElseThrow(() -> new ResourceNotFoundException("Project '" + projectId + "' not found"));
    var getProjectUri = jaggaerAPIConfig.getGetProject().get(ENDPOINT);
    var jaggaerProject = ofNullable(jaggaerWebClient.get()
        .uri(getProjectUri, dbProject.getExternalProjectId()).retrieve().bodyToMono(Project.class)
        .block(Duration.ofSeconds(jaggaerAPIConfig.getTimeoutDuration())))
            .orElseThrow(() -> new JaggaerApplicationException(INTERNAL_SERVER_ERROR.value(),
                "Unexpected error retrieving project"));

    // Get Rfx (email recipients)
    var dbEvent = getCurrentEvent(dbProject);
    var exportRfxUri = jaggaerAPIConfig.getExportRfx().get(ENDPOINT);
    var exportRfxResponse =
        ofNullable(jaggaerWebClient.get().uri(exportRfxUri, dbEvent.getExternalEventId()).retrieve()
            .bodyToMono(ExportRfxResponse.class)
            .block(ofSeconds(jaggaerAPIConfig.getTimeoutDuration())))
                .orElseThrow(() -> new JaggaerApplicationException(INTERNAL_SERVER_ERROR.value(),
                    "Unexpected error retrieving rfx"));

    // Get Project Owner
    var projectOwner = jaggaerProject.getTender().getProjectOwner();

    // Combine the user IDs and remove duplicates
    final Set<String> teamIds = jaggaerProject.getProjectTeam().getUser().stream().map(User::getId)
        .collect(Collectors.toSet());
    final Set<String> emailRecipientIds = exportRfxResponse.getEmailRecipientList()
        .getEmailRecipient().stream().map(e -> e.getUser().getId()).collect(Collectors.toSet());
    final Set<String> combinedIds = new HashSet<>();
    combinedIds.add(projectOwner.getId());
    combinedIds.addAll(teamIds);
    combinedIds.addAll(emailRecipientIds);

    // Retrieve additional info on each user from Jaggaer and Conclave
    return combinedIds.stream()
        .map(i -> getTeamMember(i, teamIds, emailRecipientIds, projectOwner.getId()))
        .filter(Objects::nonNull).collect(Collectors.toSet());
  }

  /**
   * Add an existing user to a Project Team.
   *
   * @param projectId CCS project id
   * @param userId Conclave user id (email)
   * @return
   */
  public TeamMember addProjectTeamMember(final Integer projectId, final String userId,
      final UpdateTeamMember updateTeamMember) {

    var dbProject = retryableTendersDBDelegate.findProcurementProjectById(projectId)
        .orElseThrow(() -> new ResourceNotFoundException("Project '" + projectId + "' not found"));

    var jaggaerUserId = userProfileService.resolveJaggaerUserId(userId);


    // TODO - refactor the switch below to try and share update project code

    if (updateTeamMember.getUserType() == UpdateTeamMemberType.PROJECT_OWNER
        || updateTeamMember.getUserType() == UpdateTeamMemberType.TEAM_MEMBER) {

      if (updateTeamMember.getUserType() == UpdateTeamMemberType.PROJECT_OWNER) {

      } else {

      }
    }


    // Add as Team Member
    switch (updateTeamMember.getUserType()) {
      case TEAM_MEMBER:
        var user = User.builder().id(jaggaerUserId).build();
        var projectTeam = ProjectTeam.builder().user(Collections.singleton(user)).build();
        var tender =
            Tender.builder().tenderReferenceCode(dbProject.getExternalReferenceId()).build();
        var updateProject = new CreateUpdateProject(OperationCode.CREATEUPDATE,
            Project.builder().tender(tender).projectTeam(projectTeam).build());

        var updateProjectResponse = ofNullable(
            jaggaerWebClient.post().uri(jaggaerAPIConfig.getCreateProject().get(ENDPOINT))
                .bodyValue(updateProject).retrieve().bodyToMono(CreateUpdateProjectResponse.class)
                .block(Duration.ofSeconds(jaggaerAPIConfig.getTimeoutDuration()))).orElseThrow(
                    () -> new JaggaerApplicationException(INTERNAL_SERVER_ERROR.value(),
                        "Unexpected error updating project"));

        if (updateProjectResponse.getReturnCode() != 0
            || !"OK".equals(updateProjectResponse.getReturnMessage())) {
          throw new JaggaerApplicationException(updateProjectResponse.getReturnCode(),
              updateProjectResponse.getReturnMessage());
        }
        log.info("Updated project team: {}", updateProjectResponse);
        break;
      case PROJECT_OWNER:
        var projectOwner = ProjectOwner.builder().id(jaggaerUserId).build();
        var tenderPO = Tender.builder().tenderReferenceCode(dbProject.getExternalReferenceId())
            .projectOwner(projectOwner).build();
        var updateProjectPO = new CreateUpdateProject(OperationCode.CREATEUPDATE,
            Project.builder().tender(tenderPO).build());

        var updateProjectResponsePO = ofNullable(
            jaggaerWebClient.post().uri(jaggaerAPIConfig.getCreateProject().get(ENDPOINT))
                .bodyValue(updateProjectPO).retrieve().bodyToMono(CreateUpdateProjectResponse.class)
                .block(Duration.ofSeconds(jaggaerAPIConfig.getTimeoutDuration()))).orElseThrow(
                    () -> new JaggaerApplicationException(INTERNAL_SERVER_ERROR.value(),
                        "Unexpected error updating project"));

        if (updateProjectResponsePO.getReturnCode() != 0
            || !"OK".equals(updateProjectResponsePO.getReturnMessage())) {
          throw new JaggaerApplicationException(updateProjectResponsePO.getReturnCode(),
              updateProjectResponsePO.getReturnMessage());
        }
        log.info("Updated project owner: {}", updateProjectResponsePO);
        break;
      case EMAIL_RECIPIENT:
        break;
      default:
        log.warn("No matching update team member type supplied");
    }

    return getProjectTeamMembers(projectId).stream()
        .filter(tm -> tm.getOCDS().getId().equals(jaggaerUserId)).findFirst().orElseThrow();

  }


  /**
   * Get a Team Member.
   *
   * Conclave user id is currently email address, but that may change in the future following a
   * Jaggaer update to insert a Conclave ID into the `extCode` attribute on a user in Jaggaer. This
   * (and other aspects of the code) may need to be updated in future to use this - TBD.
   *
   * @param jaggaerUserId
   * @return TeamMember
   */
  private TeamMember getTeamMember(final String jaggaerUserId, final Set<String> teamMemberIds,
      final Set<String> emailRecipientIds, final String projectOwnerId) {

    try {
      var jaggaerUser = userProfileService.resolveBuyerUserByUserId(jaggaerUserId)
          .orElseThrow(() -> new ResourceNotFoundException("Jaggaer"));
      var conclaveUser = conclaveService.getUserProfile(jaggaerUser.getEmail())
          .orElseThrow(() -> new ResourceNotFoundException("Conclave"));

      var teamMember = new TeamMember();

      var tmOCDS = new TeamMemberOCDS();
      var contact = new ContactPoint1();
      tmOCDS.setId(conclaveUser.getUserName());
      contact.setName(conclaveUser.getFirstName() + " " + conclaveUser.getLastName());
      contact.setEmail(conclaveUser.getUserName());
      contact.setTelephone(jaggaerUser.getPhoneNumber());
      // TODO: can get more contact info from Conclave via
      // conclaveService.getUserContacts(jaggaerUser.getEmail())
      // Question on logic for doing this outstanding on SCAT-2240
      // cp.setFaxNumber(null);
      // cp.setUrl(null);
      tmOCDS.setContact(contact);
      teamMember.setOCDS(tmOCDS);

      var tmNonOCDS = new TeamMemberNonOCDS();
      tmNonOCDS.setProjectOwner(jaggaerUserId.equals(projectOwnerId));
      tmNonOCDS.setTeamMember(teamMemberIds.contains(jaggaerUserId));
      tmNonOCDS.setEmailRecipient(emailRecipientIds.contains(jaggaerUserId));
      teamMember.setNonOCDS(tmNonOCDS);

      return teamMember;
    } catch (final ResourceNotFoundException rnfe) {
      log.warn("Unable to find user '{}' in {} when building Project Team, so ignoring.",
          jaggaerUserId, rnfe.getMessage());
      return null;
    } catch (final Exception e) {
      if (e.getCause() != null && e.getCause().getClass() == JaggaerApplicationException.class) {
        log.warn(
            "Unable to find user '{}' in Jaggaer user cache when building Project Team, so ignoring.",
            jaggaerUserId);
        return null;
      }
      throw new UnhandledEdgeCaseException("Unexpected exception building Project Team list", e);
    }
  }

  /**
   * Returns the current event for a project.
   *
   * TODO: what extra logic is required in the event there are multiple events on a project? (Event
   * status is not captured in the Tenders DB currently).
   */
  private ProcurementEvent getCurrentEvent(final ProcurementProject project) {

    var event = project.getProcurementEvents().stream().findFirst();
    if (event.isPresent()) {
      return event.get();
    }
    throw new UnhandledEdgeCaseException(
        "Could not find current event for project " + project.getId());
  }
}
