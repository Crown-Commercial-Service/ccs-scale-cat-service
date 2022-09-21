package uk.gov.crowncommercial.dts.scale.cat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.web.reactive.function.client.WebClient;
import uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.exception.*;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.*;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.*;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.Tender;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.*;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.User;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;
import uk.gov.crowncommercial.dts.scale.cat.utils.TendersAPIModelUtils;

import javax.transaction.Transactional;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Optional.ofNullable;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig.ENDPOINT;
import static uk.gov.crowncommercial.dts.scale.cat.model.entity.Timestamps.createTimestamps;

import javax.transaction.Transactional;

import static uk.gov.crowncommercial.dts.scale.cat.utils.TendersAPIModelUtils.getTenderPeriod;
import static uk.gov.crowncommercial.dts.scale.cat.utils.TendersAPIModelUtils.getInstantFromDate;

/**
 * Procurement projects service layer. Handles interactions with Jaggaer and the persistence layer
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProcurementProjectService {

  // TODO: Migrate these to use the JaggaerService wrapper as time allows
  private final JaggaerAPIConfig jaggaerAPIConfig;
  private final WebClient jaggaerWebClient;
  private final UserProfileService userProfileService;
  private final ConclaveService conclaveService;
  private final RetryableTendersDBDelegate retryableTendersDBDelegate;
  private final ProcurementEventService procurementEventService;

  private final EventTransitionService eventTransitionService;
  private final TendersAPIModelUtils tendersAPIModelUtils;
  private final ModelMapper modelMapper;
  private final JaggaerService jaggaerService;
  private final AgreementsService agreementsService;

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
      final String principal, final String conclaveOrgId) {

    // Fetch Jaggaer user ID and Buyer company ID from Jaggaer profile based on OIDC login id
    var jaggaerUserId = userProfileService.resolveBuyerUserProfile(principal)
        .orElseThrow(() -> new AuthorisationFailureException("Jaggaer user not found")).getUserId();
    var jaggaerBuyerCompanyId = userProfileService.resolveBuyerUserCompany(principal).getBravoId();

    var conclaveUserOrg = conclaveService.getOrganisation(conclaveOrgId)
        .orElseThrow(() -> new AuthorisationFailureException(
            "Conclave org with ID [" + conclaveOrgId + "] from JWT not found"));

    var projectTitle =
        getDefaultProjectTitle(agreementDetails, conclaveUserOrg.getIdentifier().getLegalName());

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

    var procurementProject = ProcurementProject.builder()
        .caNumber(agreementDetails.getAgreementId()).lotNumber(agreementDetails.getLotId())
        .externalProjectId(createProjectResponse.getTenderCode())
        .externalReferenceId(createProjectResponse.getTenderReferenceCode())
        .projectName(projectTitle).createdBy(principal).createdAt(Instant.now())
        .updatedBy(principal).updatedAt(Instant.now()).build();

    /*
     * Get existing buyer user org mapping or create as part of procurement project persistence.
     * Should be unique per Conclave org. Buyer Jaggaer company ID WILL repeat (e.g. for the Buyer
     * self-service company).
     */
    var organisationIdentifier = conclaveService.getOrganisationIdentifer(conclaveUserOrg);
    var organisationMapping =
        retryableTendersDBDelegate.findOrganisationMappingByOrganisationId(organisationIdentifier);

    // Adapt save strategy based on org mapping status (new/existing)
    if (organisationMapping.isEmpty()) {
      procurementProject.setOrganisationMapping(retryableTendersDBDelegate
          .save(OrganisationMapping.builder().organisationId(organisationIdentifier)
              .externalOrganisationId(Integer.valueOf(jaggaerBuyerCompanyId))
              .createdAt(Instant.now()).createdBy(principal).build()));
    } else {
      procurementProject = retryableTendersDBDelegate.save(procurementProject);
      procurementProject.setOrganisationMapping(organisationMapping.get());
    }
    retryableTendersDBDelegate.save(procurementProject);

    var eventSummary = procurementEventService.createEvent(procurementProject.getId(),
        new CreateEvent(), null, principal);

    // add current user to project
    addProjectUserMapping(jaggaerUserId, procurementProject, principal);

    return tendersAPIModelUtils.buildDraftProcurementProject(agreementDetails,
        procurementProject.getId(), eventSummary.getId(), projectTitle,
        conclaveUserOrg.getIdentifier().getLegalName());
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

    final var lotEventTypes =
        agreementsService.getLotEventTypes(project.getCaNumber(), project.getLotNumber());

    return lotEventTypes.stream()
        .map(lotEventType -> modelMapper.map(lotEventType, EventType.class))
        .collect(Collectors.toList());
  }

  /**
   * Get Project Team members. This is a combination of Project Team members on the project and
   * email recipients on the rfx.
   *
   * @param projectId CCS project id
   * @param principal
   * @return Collection of project team members
   */
  public Collection<TeamMember> getProjectTeamMembers(final Integer projectId,
      final String principal) {

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
    var exportRfxResponse = jaggaerService.getRfx(dbEvent.getExternalEventId());

    // Get Project Owner
    var projectOwner = jaggaerProject.getTender().getProjectOwner();

    // Combine the user IDs and remove duplicates
    final Set<String> teamIds = jaggaerProject.getProjectTeam().getUser().stream().map(User::getId)
        .collect(Collectors.toSet());
    final Set<String> emailRecipientIds = exportRfxResponse.getEmailRecipientList()
        .getEmailRecipient().stream().map(e -> e.getUser().getId()).collect(Collectors.toSet());
    final Set<String> combinedIds = new HashSet<>();
    combinedIds.add(projectOwner.getId());
    combinedIds.addAll(emailRecipientIds);

    // update user
    // TODO Add only valid users
    var existingList = updateProjectUserMapping(dbProject, teamIds, principal);
    var finalTeamIds =
        teamIds.stream().filter(e -> existingList.stream().filter(ProjectUserMapping::isDeleted)
            .noneMatch(k -> e.equals(k.getUserId()))).collect(Collectors.toSet());
    combinedIds.addAll(finalTeamIds);
    // Retrieve additional info on each user from Jaggaer and Conclave
    return combinedIds.stream()
        .map(i -> getTeamMember(i, finalTeamIds, emailRecipientIds, projectOwner.getId()))
        .filter(Objects::nonNull).collect(Collectors.toSet());
  }
  
  /**
   * Add/Update Project Team Member (owner, team members, email recipients).
   *
   * @param projectId CCS project id
   * @param userId Conclave user id (email)
   * @param updateTeamMember contains details of type of update to perform
   * @param principal
   * @return Team Member details
   */
  public TeamMember addProjectTeamMember(final Integer projectId, final String userId,
      final UpdateTeamMember updateTeamMember, final String principal) {

    log.debug("Add/update Project Team");

    var dbProject = retryableTendersDBDelegate.findProcurementProjectById(projectId)
        .orElseThrow(() -> new ResourceNotFoundException("Project '" + projectId + "' not found"));
    var jaggaerUser = userProfileService.resolveBuyerUserProfile(userId)
        .orElseThrow(() -> new JaggaerApplicationException("Unable to find user in Jaggaer"));
    var jaggaerUserId = jaggaerUser.getUserId();
    var user = User.builder().id(jaggaerUserId).build();

    Tender tender;
    ProjectTeam projectTeam;

    switch (updateTeamMember.getUserType()) {
      case PROJECT_OWNER:
        log.debug("Project Owner update");
        // User has to also be a Team Member before they can be a Project Owner
        projectTeam = ProjectTeam.builder().user(Collections.singleton(user)).build();
        var projectOwner = ProjectOwner.builder().id(jaggaerUserId).build();
        tender = Tender.builder().tenderReferenceCode(dbProject.getExternalReferenceId())
            .projectOwner(projectOwner).build();
        var updateProject = new CreateUpdateProject(OperationCode.CREATEUPDATE,
            Project.builder().tender(tender).projectTeam(projectTeam).build());
        jaggaerService.createUpdateProject(updateProject);
        break;
      case TEAM_MEMBER:
        log.debug("Team Member update");
        projectTeam = ProjectTeam.builder().user(Collections.singleton(user)).build();
        tender = Tender.builder().tenderReferenceCode(dbProject.getExternalReferenceId()).build();
        updateProject = new CreateUpdateProject(OperationCode.CREATEUPDATE,
            Project.builder().tender(tender).projectTeam(projectTeam).build());
        jaggaerService.createUpdateProject(updateProject);
        break;
      case EMAIL_RECIPIENT:
        log.debug("EMail Recipient update");
        var event = getCurrentEvent(dbProject);
        var emailRecipient = EmailRecipient.builder().user(user).build();
        var emailRecipients =
            EmailRecipientList.builder().emailRecipient(Arrays.asList(emailRecipient)).build();
        var rfxSetting = RfxSetting.builder().rfxId(event.getExternalEventId())
            .rfxReferenceCode(event.getExternalReferenceId()).build();
        var rfx = Rfx.builder().rfxSetting(rfxSetting).emailRecipientList(emailRecipients).build();
        jaggaerService.createUpdateRfx(rfx, OperationCode.CREATEUPDATE);
        break;
      default:
        log.warn("No matching update team member type supplied");
        throw new IllegalArgumentException("Unknown Team Member Update Type");
    }

    addProjectUserMapping(jaggaerUserId, dbProject, principal);
    return getProjectTeamMembers(projectId, principal).stream()
        .filter(tm -> tm.getOCDS().getId().equalsIgnoreCase(userId)).findFirst().orElseThrow();
  }

  public void deleteTeamMember(final Integer projectId, final String userId,
      final String principal) {

    log.debug("delete Project Team member");
    var dbProject = retryableTendersDBDelegate.findProcurementProjectById(projectId)
        .orElseThrow(() -> new ResourceNotFoundException("Project '" + projectId + "' not found"));
    var jaggaerUser = userProfileService.resolveBuyerUserProfile(userId)
        .orElseThrow(() -> new JaggaerApplicationException("Unable to find user in Jaggaer"));
    var jaggaerUserId = jaggaerUser.getUserId();
    var jaggaerProject = jaggaerService.getProject(dbProject.getExternalProjectId());

    // Get Project Owner
    var projectOwner = jaggaerProject.getTender().getProjectOwner();
    if (projectOwner.getId().equals(jaggaerUserId)) {
      log.warn("Unable to delete project owner");
      throw new IllegalArgumentException("Unable to delete project owner");
    }

    // update team member as deleted
    deleteProjectUserMapping(jaggaerUserId, dbProject, principal);
  }

  /**
   * Get Projects
   *
   * @return Collection of projects
   * @param principal
   */
  public Collection<ProjectPackageSummary> getProjects(final String principal) {

    log.debug("Get projects for user: " + principal);

    // Fetch Jaggaer ID and Buyer company ID from Jaggaer profile based on OIDC login id
    var jaggaerUserId = userProfileService.resolveBuyerUserProfile(principal)
        .orElseThrow(() -> new AuthorisationFailureException("Jaggaer user not found")).getUserId();

    var projectUserMappings = retryableTendersDBDelegate.findProjectUserMappingByUserId(
        jaggaerUserId, PageRequest.of(0, 20, Sort.by("timestamps.createdAt").descending()));

    var externalEventIdsAllProjects = projectUserMappings.stream()
        .flatMap(pum -> pum.getProject().getProcurementEvents().stream())
        .map(ProcurementEvent::getExternalEventId).collect(Collectors.toSet());

    var projectUserRfxs = jaggaerService.searchRFx(externalEventIdsAllProjects);

    if (!CollectionUtils.isEmpty(projectUserMappings)) {
      return projectUserMappings.stream()
          .map(pum -> convertProjectToProjectPackageSummary(pum, projectUserRfxs))
          .filter(Optional::isPresent).map(Optional::get).collect(Collectors.toSet());
    }
    return Collections.emptyList();
  }

  /**
   * Convert mapping to mapping package summary
   *
   * @param mapping the mapping
   * @return ProjectPackageSummary
   */
  private Optional<ProjectPackageSummary> convertProjectToProjectPackageSummary(
      final ProjectUserMapping mapping, final Set<ExportRfxResponse> projectUserRfxs) {

    log.trace("Convert Project to ProjectPackageSummary: " + mapping.getProject().getId());

    // Get Project from database
    var projectPackageSummary = new ProjectPackageSummary();
    var agreementNo = mapping.getProject().getCaNumber();
    var dbEvent = getCurrentEvent(mapping.getProject());
    // TODO make single call instead of 2
    try {
      log.trace("Get agreement and lots: " + agreementNo);
      var agreementDetails = agreementsService.getAgreementDetails(agreementNo);
      var lotDetails =
          agreementsService.getLotDetails(agreementNo, mapping.getProject().getLotNumber());
      projectPackageSummary.setAgreementName(agreementDetails.getName());
      projectPackageSummary.setLotName(lotDetails.getName());
    } catch (Exception e) {
      // ignore for the moment, replace when single method to get all data from agreement service
      log.error("Error retrieving agreement and lots: " + e.getMessage());
    }
    // TODO no value for Uri
    // projectPackageSummary.setUri(getProjectUri);
    projectPackageSummary.setAgreementId(mapping.getProject().getCaNumber());
    projectPackageSummary.setLotId(mapping.getProject().getLotNumber());
    projectPackageSummary.setProjectId(mapping.getProject().getId());
    projectPackageSummary.setProjectName(mapping.getProject().getProjectName());

    EventSummary eventSummary = null;
    RfxSetting rfxSetting = null;

    if (dbEvent.isTendersDBOnly() || dbEvent.getExternalEventId() == null) {
      log.trace("Get Event from Tenders DB: {}", dbEvent.getId());
      eventSummary = tendersAPIModelUtils.buildEventSummary(dbEvent.getEventID(),
          dbEvent.getEventName(), Optional.ofNullable(dbEvent.getExternalReferenceId()),
          ViewEventType.fromValue(dbEvent.getEventType()), TenderStatus.PLANNING, ReleaseTag.TENDER,
          Optional.ofNullable(dbEvent.getAssessmentId()));

          eventSummary.setTenderPeriod(getTenderPeriod(dbEvent.getPublishDate(),dbEvent.getCloseDate()));


    } else {
      log.trace("Get Rfx from Jaggaer: {}", dbEvent.getExternalEventId());
      try {
        eventSummary = tendersAPIModelUtils.buildEventSummary(dbEvent.getEventID(),
            dbEvent.getEventName(), Optional.ofNullable(dbEvent.getExternalReferenceId()),
            Objects.nonNull(dbEvent.getEventType())
                ? ViewEventType.fromValue(dbEvent.getEventType())
                : null,
            Objects.nonNull(dbEvent.getTenderStatus())
                ? TenderStatus.fromValue(dbEvent.getTenderStatus())
                : null,
            ReleaseTag.TENDER, Optional.ofNullable(dbEvent.getAssessmentId()));

        // We need to build event summary before irrespective of jaggaer response
        var exportRfxResponse = projectUserRfxs.stream()
            .filter(
                rfx -> Objects.equals(dbEvent.getExternalEventId(), rfx.getRfxSetting().getRfxId()))
            .findFirst().orElseThrow(
                () -> new TendersDBDataException("Unexplained data mismatch from Rfx search"));
        rfxSetting = exportRfxResponse.getRfxSetting();
        // update the tender period from rfx
        // fixed for SCAT-6566  : if the closed date from tender db event has a value it takes precedence
        eventSummary.setTenderPeriod(getTenderPeriod(getInstantFromDate(rfxSetting.getPublishDate()),null!=dbEvent.getCloseDate()?dbEvent.getCloseDate():getInstantFromDate(rfxSetting.getCloseDate())));

      } catch (Exception e) {
        // No data found in Jagger
        log.debug("Unable to find RFX records for event id : " + dbEvent.getExternalEventId());
      }
    }

    if(null != eventSummary) {
      eventSummary.setDashboardStatus(tendersAPIModelUtils.getDashboardStatus(rfxSetting, dbEvent));
      projectPackageSummary.activeEvent(eventSummary);
    }

    return Optional.of(projectPackageSummary);
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

  private void addProjectUserMapping(final String jaggaerUserId, final ProcurementProject project,
      final String principal) {
    retryableTendersDBDelegate
        .findProjectUserMappingByProjectIdAndUserId(project.getId(), jaggaerUserId)
        .ifPresentOrElse(userMapping -> {
          // Update existed deleted userMapping
          userMapping.setDeleted(false);
          userMapping
              .setTimestamps(Timestamps.updateTimestamps(userMapping.getTimestamps(), principal));
          retryableTendersDBDelegate.save(userMapping);
        }, () -> retryableTendersDBDelegate.save(ProjectUserMapping.builder().project(project)
            .userId(jaggaerUserId).timestamps(createTimestamps(principal)).build()));
  }

  private void deleteProjectUserMapping(final String jaggaerUserId,
      final ProcurementProject project, final String principal) {

    var projectUserMapping = retryableTendersDBDelegate
        .findProjectUserMappingByProjectIdAndUserId(project.getId(), jaggaerUserId);

    if (!projectUserMapping.isPresent()) {
      var error = String.format("ProjectUserMapping not found for project %s and userId %s",
          project.getId(), jaggaerUserId);
      log.warn(error);
      throw new ResourceNotFoundException(error);
    }

    var userMapping = projectUserMapping.get();
    userMapping.setDeleted(true);
    userMapping.setTimestamps(Timestamps.updateTimestamps(userMapping.getTimestamps(), principal));
    retryableTendersDBDelegate.save(userMapping);
  }

  private Set<ProjectUserMapping> updateProjectUserMapping(final ProcurementProject project,
      final Set<String> teamIds, final String principal) {

    var existingMappings =
        retryableTendersDBDelegate.findProjectUserMappingByProjectId(project.getId());
    var addMappingList = new ArrayList<ProjectUserMapping>();

    // Add any users, who do not exists in database
    for (String teamId : teamIds) {
      var userMapping = existingMappings.stream()
          .filter(projectUserMapping -> projectUserMapping.getUserId().equals(teamId)).findFirst();
      if (!userMapping.isPresent()) {
        addMappingList.add(ProjectUserMapping.builder().project(project).userId(teamId)
            .timestamps(createTimestamps(principal)).build());
      }
    }

    // update deleted users, who are not in users list
    addMappingList.addAll(existingMappings.stream()
        .filter(projectUserMapping -> !teamIds.contains(projectUserMapping.getUserId()))
        .map(m -> ProjectUserMapping.builder().id(m.getId()).project(m.getProject())
            .userId(m.getUserId()).deleted(true)
            .timestamps(Timestamps.updateTimestamps(m.getTimestamps(), principal)).build())
        .collect(Collectors.toList()));

    if (!CollectionUtils.isEmpty(addMappingList)) {
      existingMappings.addAll(retryableTendersDBDelegate.saveAll(addMappingList));
    }
    return existingMappings;
  }

  /**
   *
   * @param projectId
   * @param terminationType
   * @param principal
   */
  @Transactional
  public void closeProcurementProject(final Integer projectId, final TerminationType terminationType,
      final String principal) {
    var procurementEvents = retryableTendersDBDelegate.findProcurementEventsByProjectId(projectId);
    if (CollectionUtils.isEmpty(procurementEvents)) {
      log.info("No events exists for this project");
    } else {
      procurementEvents.forEach(
              event -> {
                eventTransitionService.terminateEvent(
                    projectId, event.getEventID(), terminationType, principal, false);
              });
    }
  }
}
