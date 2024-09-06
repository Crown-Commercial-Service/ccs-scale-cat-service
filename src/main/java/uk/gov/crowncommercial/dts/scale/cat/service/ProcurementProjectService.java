package uk.gov.crowncommercial.dts.scale.cat.service;

import static java.util.Optional.ofNullable;
import static org.opensearch.index.query.QueryBuilders.boolQuery;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig.ENDPOINT;
import static uk.gov.crowncommercial.dts.scale.cat.model.entity.Timestamps.createTimestamps;
import static uk.gov.crowncommercial.dts.scale.cat.service.scheduler.ProjectsCSVGenerationScheduledTask.CSV_FILE_NAME;
import static uk.gov.crowncommercial.dts.scale.cat.service.scheduler.ProjectsCSVGenerationScheduledTask.CSV_FILE_PREFIX;
import static uk.gov.crowncommercial.dts.scale.cat.utils.TendersAPIModelUtils.getInstantFromDate;
import static uk.gov.crowncommercial.dts.scale.cat.utils.TendersAPIModelUtils.getTenderPeriod;
import java.io.InputStream;
import java.net.URI;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.modelmapper.ModelMapper;
import org.opensearch.data.client.orhlc.NativeSearchQuery;
import org.opensearch.data.client.orhlc.NativeSearchQueryBuilder;
import org.opensearch.index.query.*;
import org.opensearch.search.aggregations.AggregationBuilders;
import org.opensearch.search.aggregations.Aggregations;
import org.opensearch.search.aggregations.bucket.terms.Terms;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriUtils;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.S3Object;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.config.paas.AWSS3Service;
import uk.gov.crowncommercial.dts.scale.cat.exception.AuthorisationFailureException;
import uk.gov.crowncommercial.dts.scale.cat.exception.JaggaerApplicationException;
import uk.gov.crowncommercial.dts.scale.cat.exception.JaggaerUserNotExistException;
import uk.gov.crowncommercial.dts.scale.cat.exception.ResourceNotFoundException;
import uk.gov.crowncommercial.dts.scale.cat.exception.TendersDBDataException;
import uk.gov.crowncommercial.dts.scale.cat.exception.UnhandledEdgeCaseException;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.AgreementDetail;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.OrganisationMapping;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementProject;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProjectUserMapping;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.Timestamps;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.AgreementDetails;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.ContactPoint1;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.CreateEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.DashboardStatus;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.DraftProcurementProject;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.EventSummary;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.Links1;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.ProjectEventType;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.ProjectFilter;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.ProjectFilters;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.ProjectLots;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.ProjectPackageSummary;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.ProjectPublicSearchResult;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.ProjectPublicSearchSummary;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.ProjectSearchCriteria;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.ReleaseTag;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.TeamMember;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.TeamMemberNonOCDS;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.TeamMemberOCDS;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.TenderStatus;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.TerminationType;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.UpdateTeamMember;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.ViewEventType;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.*;
import uk.gov.crowncommercial.dts.scale.cat.model.search.ProcurementEventSearch;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;
import uk.gov.crowncommercial.dts.scale.cat.repo.search.SearchProjectRepo;
import uk.gov.crowncommercial.dts.scale.cat.utils.TendersAPIModelUtils;

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
  private final SearchProjectRepo searchProjectRepo;
  private final EventTransitionService eventTransitionService;
  private final TendersAPIModelUtils tendersAPIModelUtils;
  private final ModelMapper modelMapper;
  private final JaggaerService jaggaerService;
  private final AgreementsService agreementsService;

  private final ElasticsearchOperations elasticsearchOperations;
  private final AmazonS3 tendersS3Client;
  private final AWSS3Service tendersS3Service;


  private static final String PROJECT_NAME = "projectName";
  private static final String PROJECT_DESCRIPTION = "description";
  private static final String LOT = "lot";

  private static final String STATUS = "status";

  private static final String COUNT_AGGREGATION = "count_lot";
  private static final String SEARCH_URI = "/tenders/projects/search?agreement-id=RM1043.8&keyword=%s&page=%s&page-size=%s";


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
  public DraftProcurementProject createFromAgreementDetails(final AgreementDetails agreementDetails, final String principal, final String conclaveOrgId) {
    // Before we do anything, check if we've been given a lot ID as part of the request
    boolean validRequest = true;

    if (agreementDetails.getLotId() == null || agreementDetails.getLotId().isEmpty()) {
      // No lot ID has been provided - so we need to check via the Agreements Service whether or not a lot is required for this agreement
      AgreementDetail agreementModel = agreementsService.getAgreementDetails(agreementDetails.getAgreementId());

      if (agreementModel == null || agreementModel.getPreDefinedLotRequired() == null || agreementModel.getPreDefinedLotRequired()) {
        // The agreement either could not be fetched or it requires a lot.  This is not a valid request
        validRequest = false;
      }
    }

    if (validRequest) {
      // Fetch Jaggaer user ID and Buyer company ID from Jaggaer profile based on OIDC login id
      var jaggaerUserId = userProfileService.resolveBuyerUserProfile(principal)
              .orElseThrow(() -> new AuthorisationFailureException("Jaggaer user not found")).getUserId();
      var jaggaerBuyerCompanyId = userProfileService.resolveBuyerUserCompany(principal).getBravoId();

      var conclaveUserOrg = conclaveService.getOrganisationIdentity(conclaveOrgId)
              .orElseThrow(() -> new AuthorisationFailureException(
                      "Conclave org with ID [" + conclaveOrgId + "] from JWT not found"));

      var projectTitle = getDefaultProjectTitle(agreementDetails, conclaveUserOrg.getIdentifier().getLegalName());
      String projectTemplateId = null;

      if (jaggaerAPIConfig.getCreateProjectTemplateId() != null && jaggaerAPIConfig.getCreateProjectTemplateId().isPresent()) {
        projectTemplateId = jaggaerAPIConfig.getCreateProjectTemplateId().get();
      }

      var tender = Tender.builder().title(projectTitle)
              .buyerCompany(BuyerCompany.builder().id(jaggaerBuyerCompanyId).build())
              .projectOwner(ProjectOwner.builder().id(jaggaerUserId).build())
              .sourceTemplateReferenceCode(projectTemplateId).build();

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

      log.info("Start calling Jaggaer API to Create or Update project. Request: {}", createUpdateProject);
      var createProjectResponse =
              ofNullable(jaggaerWebClient.post().uri(jaggaerAPIConfig.getCreateProject().get(ENDPOINT))
                      .bodyValue(createUpdateProject).retrieve().bodyToMono(CreateUpdateProjectResponse.class)
                      .block(Duration.ofSeconds(jaggaerAPIConfig.getTimeoutDuration())))
                      .orElseThrow(() -> new JaggaerApplicationException(INTERNAL_SERVER_ERROR.value(),
                              "Unexpected error creating project"));
      log.info("Finish calling Jaggaer API to Create or Update project. Response: {}", createProjectResponse);

      if (createProjectResponse.getReturnCode() != 0 || !"OK".equals(createProjectResponse.getReturnMessage())) {
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
      var organisationMapping = retryableTendersDBDelegate.findOrganisationMappingByOrganisationId(organisationIdentifier);

      // Adapt save strategy based on org mapping status (new/existing)
      if (organisationMapping.isEmpty()) {
        organisationMapping = Optional.of(retryableTendersDBDelegate
                .save(OrganisationMapping.builder().organisationId(organisationIdentifier)
                        .externalOrganisationId(Integer.valueOf(jaggaerBuyerCompanyId))
                        .createdAt(Instant.now()).createdBy(principal).build()));
      }

      procurementProject.setOrganisationMapping(organisationMapping.get());
      procurementProject = retryableTendersDBDelegate.save(procurementProject);

      var eventSummary = procurementEventService.createEvent(procurementProject.getId(),
              new CreateEvent(), null, principal);

      // add current user to project
      addProjectUserMapping(jaggaerUserId, procurementProject, principal);

      return tendersAPIModelUtils.buildDraftProcurementProject(agreementDetails,
              procurementProject.getId(), eventSummary.getId(), projectTitle,
              conclaveUserOrg.getIdentifier().getLegalName());
    } else {
      throw new AuthorisationFailureException("A lot is required for this commercial agreement.");
    }
  }

  String getDefaultProjectTitle(final AgreementDetails agreementDetails,
      final String organisation) {
    if (!agreementDetails.getLotId().isEmpty()) {
      return String.format(jaggaerAPIConfig.getCreateProject().get("defaultTitleFormat"), agreementDetails.getAgreementId(), agreementDetails.getLotId(), organisation);
    } else {
      return String.format("%s-%s", agreementDetails.getAgreementId(), organisation);
    }
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

    log.info("Start calling Jaggaer API to Update project. Request: {}", updateProject);
    var updateProjectResponse =
        ofNullable(jaggaerWebClient.post().uri(jaggaerAPIConfig.getCreateProject().get(ENDPOINT))
            .bodyValue(updateProject).retrieve().bodyToMono(CreateUpdateProjectResponse.class)
            .block(Duration.ofSeconds(jaggaerAPIConfig.getTimeoutDuration())))
                .orElseThrow(() -> new JaggaerApplicationException(INTERNAL_SERVER_ERROR.value(),
                    "Unexpected error updating project"));
    log.info("Finish calling Jaggaer API to Update project. Response: {}", updateProjectResponse);

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
  public Collection<ProjectEventType> getProjectEventTypes(final Integer projectId) {

    final ProcurementProject project = retryableTendersDBDelegate.findProcurementProjectById(projectId)
        .orElseThrow(() -> new ResourceNotFoundException("Project '" + projectId + "' not found"));

    final var lotEventTypes =
        agreementsService.getLotEventTypes(project.getCaNumber(), project.getLotNumber());

    //TODO to be removed after the yaml is fixed to include the templateId.
    lotEventTypes.stream().filter(let -> (let.getTemplateGroups() !=  null)).forEach(lotEventType -> {
      lotEventType.getTemplateGroups().stream().forEach(questionTemplate -> { questionTemplate.setTemplateGroupId(questionTemplate.getTemplateId());});
    });


    return lotEventTypes.stream()
        .map(lotEventType -> modelMapper.map(lotEventType, ProjectEventType.class))
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

    log.info("Start calling Jaggaer API to get project using project Id: {}", projectId);
    var jaggaerProject = ofNullable(jaggaerWebClient.get()
        .uri(getProjectUri, dbProject.getExternalProjectId()).retrieve().bodyToMono(Project.class)
        .block(Duration.ofSeconds(jaggaerAPIConfig.getTimeoutDuration())))
            .orElseThrow(() -> new JaggaerApplicationException(INTERNAL_SERVER_ERROR.value(),
                "Unexpected error retrieving project"));
    log.info("Finish calling Jaggaer API to get project using project Id: {}", projectId);

    // Get Rfx (email recipients)
    var dbEvent = getCurrentEvent(dbProject);

    var exportRfxResponse = jaggaerService.getRfxWithEmailRecipients(dbEvent.getExternalEventId());

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
        .orElseThrow(() -> new JaggaerUserNotExistException("Unable to find user in Jaggaer"));
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

    // check email-recipient in jaggaer
    var event = getCurrentEvent(dbProject);
    var exportRfxResponse = jaggaerService.getRfxWithEmailRecipients(event.getExternalEventId());

    Optional<EmailRecipient> isEmailRecipient =
        exportRfxResponse.getEmailRecipientList().getEmailRecipient().stream()
            .filter(e -> e.getUser().getId().equals(jaggaerUserId)).findAny();

    if (isEmailRecipient.isPresent()) {
      deleteEmailRecipientInJaggaer(event, jaggaerUserId, exportRfxResponse);
    }
    // update team member as deleted
    deleteProjectUserMapping(jaggaerUserId, dbProject, principal);
  }

  private void deleteEmailRecipientInJaggaer(ProcurementEvent event, String jaggaerUserId,
                                             ExportRfxResponse exportRfxResponse){
    log.debug("delete email-recipient in jaggaer");
    List<EmailRecipient> newEmailRecipientsList =
            exportRfxResponse.getEmailRecipientList().getEmailRecipient().stream()
                    .filter(e -> !e.getUser().getId().equals(jaggaerUserId)).toList();
    var rfxRequest = Rfx.builder()
            .rfxSetting(RfxSetting.builder().rfxId(event.getExternalEventId())
                    .rfxReferenceCode(event.getExternalReferenceId()).build())
            .emailRecipientList(
                    EmailRecipientList.builder().emailRecipient(newEmailRecipientsList).build())
            .build();
    jaggaerService.createUpdateRfx(rfxRequest, OperationCode.UPDATE_RESET);
  }

  /**
   * Get Projects
   *
   * @param principal
   * @param searchType
   * @param searchTerm
   * @param page
   * @param pageSize
   * @return Collection of projects
   */
  public Collection<ProjectPackageSummary> getProjects(final String principal, final String searchType,final String searchTerm,
                                                       String page, String pageSize) {

    log.debug("Get projects for user: " + principal);

    // Fetch Jaggaer ID and Buyer company ID from Jaggaer profile based on OIDC login id
    var jaggaerUserId = userProfileService.resolveBuyerUserProfile(principal)
        .orElseThrow(() -> new AuthorisationFailureException("Jaggaer user not found")).getUserId();


    var projectUserMappings = retryableTendersDBDelegate.findProjectUserMappingByUserId(
                                                      jaggaerUserId,
                                                      searchType,
                                                      searchTerm,
                                                      PageRequest.of(Objects.nonNull(page)?Integer.valueOf(page):0, Objects.nonNull(pageSize)?Integer.valueOf(pageSize):20, Sort.by("project.procurementEvents.updatedAt").descending()));

    if (!CollectionUtils.isEmpty(projectUserMappings)) {
      var externalEventIdsAllProjects = projectUserMappings.stream()
          .flatMap(pum -> pum.getProject().getProcurementEvents().stream())
          .map(ProcurementEvent::getExternalEventId).collect(Collectors.toSet());
      var projectUserRfxs = jaggaerService.searchRFx(externalEventIdsAllProjects);
      
      return projectUserMappings.stream()
          .map(pum -> convertProjectToProjectPackageSummary(pum, projectUserRfxs))
          .filter(Optional::isPresent).map(Optional::get)
          .collect(Collectors.toSet());
    }
    return Collections.emptyList();
  }

  /**
   * Get a specific project summary
   */
  public ProjectPackageSummary getProjectSummary(final String principal, final Integer projectId) {
    // Grab the Jaegger details we need to start from the authentication
    String userId = userProfileService.resolveBuyerUserProfile(principal).orElseThrow(() -> new AuthorisationFailureException("Jaggaer user not found")).getUserId();

    if (userId != null && !userId.isEmpty()) {
      // Now fetch the projects mapped against this user from our Tenders DB
      List<ProjectUserMapping> userProjects = retryableTendersDBDelegate.findProjectUserMappingByUserId(userId,null,null, PageRequest.of(0, 20, Sort.by("project.procurementEvents.updatedAt").descending()));

      if (userProjects != null && !userProjects.isEmpty()) {
        // Now we need to filter down to only include the project we're after
        ProjectUserMapping projectMapping = userProjects.stream()
                .filter(p -> p.getProject().getId().equals(projectId))
                .findFirst()
                .orElse(null);

        if (projectMapping != null) {
          // Great, now fetch the final details we need and map to our summary model
          Set<String> externalEventIds = projectMapping.getProject().getProcurementEvents().stream().map(ProcurementEvent::getExternalEventId).collect(Collectors.toSet());

          if (!externalEventIds.isEmpty()) {
            Set<ExportRfxResponse> projectRfxs = jaggaerService.searchRFx(externalEventIds);

            if (!projectRfxs.isEmpty()) {
              Optional<ProjectPackageSummary> projectSummary = convertProjectToProjectPackageSummary(projectMapping, projectRfxs);

              if (projectSummary.isPresent()) {
                return projectSummary.get();
              }
            }
          }
        }
      }
    }
    
    throw new ResourceNotFoundException("Project '" + projectId + "' not found");
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
      log.trace("Error retrieving agreement and lots: " + e.getMessage());
    }
    // TODO no value for Uri
    // projectPackageSummary.setUri(getProjectUri);
    projectPackageSummary.setAgreementId(mapping.getProject().getCaNumber());
    projectPackageSummary.setLotId(mapping.getProject().getLotNumber());
    projectPackageSummary.setProjectId(mapping.getProject().getId());
    projectPackageSummary.setProjectName(mapping.getProject().getProjectName());
    projectPackageSummary.setSupportId(mapping.getProject().getExternalReferenceId());

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
        eventSummary.setTenderPeriod(getTenderPeriod(getInstantFromDate(rfxSetting.getPublishDate()),
                (tendersAPIModelUtils.getDashboardStatus(rfxSetting, dbEvent).equals(DashboardStatus.CLOSED) && null!=dbEvent.getCloseDate())?
                        dbEvent.getCloseDate():getInstantFromDate(rfxSetting.getCloseDate())));

      } catch (Exception e) {
        // No data found in Jagger
        log.debug("Unable to find RFX records for event id : " + dbEvent.getExternalEventId());
      }
    }

    if(null != eventSummary) {
      eventSummary.setDashboardStatus(tendersAPIModelUtils.getDashboardStatus(rfxSetting, dbEvent));
      eventSummary.setLastUpdated(OffsetDateTime.ofInstant(dbEvent.getUpdatedAt(),ZoneId.systemDefault()));
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
      if (Objects.isNull(jaggaerUser.getSsoCodeData())){
        throw new ResourceNotFoundException("Conclave");
      }
      var conclaveUser = conclaveService.getUserProfile(jaggaerUser.getSsoCodeData()
                      .getSsoCode().stream().findFirst().get().getSsoUserLogin())
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
   */
  private ProcurementEvent getCurrentEvent(final ProcurementProject project) {
    // Sort the list of events for the project by the date last updated to be sure we consistently deal with the latest
    List<ProcurementEvent> eventsList = new ArrayList<>(project.getProcurementEvents().stream().toList());
    eventsList.sort(Comparator.comparing(ProcurementEvent::getUpdatedAt).reversed());

    Optional<ProcurementEvent> event = eventsList.stream().findFirst();

    if (event.isPresent()) {
      // We've found the latest event, so return it
      return event.get();
    }

    // If we have gotten this far it couldn't find an event, so throw an error
    throw new UnhandledEdgeCaseException("Could not find current event for project " + project.getId());
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
      procurementEvents.forEach(event -> eventTransitionService.terminateEvent(projectId, event.getEventID(), terminationType, principal));
    }
  }

  public ProjectPublicSearchResult getProjectSummery(final String keyword, final String lotId, int page, int pageSize, ProjectFilters projectFilters) {
    ProjectPublicSearchResult projectPublicSearchResult = new ProjectPublicSearchResult();
    ProjectSearchCriteria searchCriteria= new ProjectSearchCriteria();
    searchCriteria.setKeyword(keyword);
    searchCriteria.setFilters(projectFilters!=null ? projectFilters.getFilters() : null);

    NativeSearchQuery searchQuery = getSearchQuery(keyword, PageRequest.of(page,pageSize), lotId, projectFilters!=null ? projectFilters.getFilters().stream().findFirst().get() : null);
    NativeSearchQuery searchCountQuery = getLotCount(keyword,lotId, projectFilters!=null ? projectFilters.getFilters().stream().findFirst().get() : null);
    SearchHits<ProcurementEventSearch> results = elasticsearchOperations.search(searchQuery, ProcurementEventSearch.class);
    SearchHits<ProcurementEventSearch> countResults = elasticsearchOperations.search(searchCountQuery, ProcurementEventSearch.class);

    searchCriteria.setLots(getProjectLots(countResults, lotId));
    projectPublicSearchResult.setSearchCriteria(searchCriteria);
    projectPublicSearchResult.setResults(convertResults(results));
    projectPublicSearchResult.setTotalResults((int) results.getTotalHits());
    projectPublicSearchResult.setLinks(generateLinks(keyword, page, pageSize, (int) results.getTotalHits()));

    return projectPublicSearchResult;
  }

  public static <T> Predicate<T> distinctByKey(Function<? super T, Object> keyExtractor)
  {
    Map<Object, Boolean> map = new ConcurrentHashMap<>();
    return t -> map.putIfAbsent(keyExtractor.apply(t), Boolean.TRUE) == null;
  }
  private List<ProjectLots> getProjectLots(SearchHits<ProcurementEventSearch> countResults, String lotId) {
    Map<String, String> lotAndDescription = countResults.stream().map(SearchHit::getContent).filter(distinctByKey(project -> project.getLot())).collect(Collectors.toMap(ProcurementEventSearch::getLot, ProcurementEventSearch::getLotDescription));
    Aggregations aggregations = (Aggregations) countResults.getAggregations().aggregations();
     Terms terms = (Terms) aggregations.get(COUNT_AGGREGATION);
     return terms.getBuckets().stream().map(bucket -> {
       int lotnumber = Character.getNumericValue(bucket.getKeyAsString().charAt(bucket.getKeyAsString().length() - 1));
       ProjectLots projectLots= new ProjectLots();
       projectLots.setId(lotnumber);
        projectLots.setCount((int)bucket.getDocCount());
        projectLots.setText(lotAndDescription.get(bucket.getKeyAsString()));
        projectLots.setSelected(bucket.getKeyAsString().equalsIgnoreCase(lotId));
       return projectLots;
     }).collect(Collectors.toList());
  }

  private  NativeSearchQuery getSearchQuery (String keyword, PageRequest pageRequest, String lotId, ProjectFilter projectFilter) {
    NativeSearchQueryBuilder searchQueryBuilder = getFilterQuery(lotId,projectFilter, keyword);

    searchQueryBuilder.withPageable(PageRequest.of(pageRequest.getPageNumber()-1, pageRequest.getPageSize(), Sort.by(Sort.Direction.DESC, "lastUpdated")));
    NativeSearchQuery searchQuery = searchQueryBuilder.build();

    return searchQuery;
  }

  private static NativeSearchQueryBuilder getFilterQuery(String lotId, ProjectFilter projectFilter, String keyword) {
    NativeSearchQueryBuilder searchQueryBuilder = new NativeSearchQueryBuilder();
    BoolQueryBuilder boolQuery = boolQuery();
    BoolQueryBuilder statusboolQuery = boolQuery();
    if(projectFilter !=null && projectFilter.getName().equalsIgnoreCase(STATUS)) {
      projectFilter.getOptions().stream().filter(projectFilterOption -> projectFilterOption.getSelected()).forEach(projectFilterOption -> {
        statusboolQuery.should(QueryBuilders.termQuery(STATUS, projectFilterOption.getText()));
      });
      boolQuery.filter(statusboolQuery);
    }
    if(lotId != null) {
      boolQuery.must(QueryBuilders.termQuery(LOT, lotId));
    }

    addKeywordQuery(boolQuery, keyword);
    if(projectFilter !=null || lotId !=null || keyword !=null )
    {
      searchQueryBuilder.withQuery(boolQuery);
    }
    return searchQueryBuilder;
  }

  private static void addKeywordQuery(BoolQueryBuilder boolQuery, String keyword) {
    if(keyword != null && keyword.trim().length() > 0) {
        boolQuery.must(QueryBuilders.simpleQueryStringQuery(keyword)
                .field(PROJECT_NAME).field(PROJECT_DESCRIPTION)
                .analyzeWildcard(true)
              .defaultOperator(Operator.OR).flags(SimpleQueryStringFlag.ALL));
    }
  }

  private NativeSearchQuery getLotCount (String keyword, String lotId, ProjectFilter projectFilter) {
    log.warn("Page size should be " + (int) searchProjectRepo.count());
    NativeSearchQueryBuilder searchQueryBuilder = getFilterQuery(null,projectFilter, keyword);
    searchQueryBuilder.withPageable(PageRequest.of(0, (int) searchProjectRepo.count()));
    searchQueryBuilder.withAggregations(AggregationBuilders.terms(COUNT_AGGREGATION).field("lot.raw").size(100));
    
    NativeSearchQuery searchQuery = searchQueryBuilder.build();

    return searchQuery;
  }

  private List<ProjectPublicSearchSummary> convertResults(SearchHits<ProcurementEventSearch> results)
  {
    return results.stream().map(SearchHit::getContent).map(object ->
    {
      ProjectPublicSearchSummary projectPublicSearchSummary=new ProjectPublicSearchSummary();
      projectPublicSearchSummary.setProjectId(object.getProjectId());
      projectPublicSearchSummary.setProjectName(object.getProjectName());
      projectPublicSearchSummary.setAgreement(object.getAgreement());
      projectPublicSearchSummary.setBudgetRange(object.getBudgetRange());
      projectPublicSearchSummary.setDescription(object.getDescription());
      projectPublicSearchSummary.setBuyerName(object.getBuyerName());
      projectPublicSearchSummary.setLot(object.getLot());
      projectPublicSearchSummary.setLotName(object.getLotDescription());
      projectPublicSearchSummary.setStatus(ProjectPublicSearchSummary.StatusEnum.fromValue(object.getStatus()));
      projectPublicSearchSummary.setSubStatus(object.getSubStatus());
      projectPublicSearchSummary.setLocation(object.getLocation());
      return projectPublicSearchSummary;}).collect(Collectors.toList());

  }

  private Links1 generateLinks(String keyword, int page, int pageSize, int totalsize)
  {
      int last = (int) Math.ceil((double)totalsize/pageSize);
    int next = page < last ? page + 1 : 0;
    int previous = page <= 1 ? 0 : page - 1;
    keyword = UriUtils.encode(keyword,"UTF-8");
     Links1 links1= new Links1();
     links1.setFirst(URI.create(String.format(SEARCH_URI,keyword,1,pageSize)));
     links1.setLast(last ==0 ? URI.create("") : URI.create(String.format(SEARCH_URI,keyword,last,pageSize)));
     links1.setNext(next == 0 ? URI.create("") : URI.create(String.format(SEARCH_URI,keyword,next,pageSize)));
     links1.setPrev(previous == 0 ? URI.create("") : URI.create(String.format(SEARCH_URI,keyword,previous,pageSize)));
     links1.setSelf(URI.create(String.format(SEARCH_URI,keyword,page,pageSize)));
    return links1;

  }
  
  /**
   * Download all oppertunities data from s3
   * 
   */
  public InputStream downloadProjectsData() {
    try {
      S3Object tendersS3Object = tendersS3Client
          .getObject(tendersS3Service.getCredentials().getBucketName(), CSV_FILE_PREFIX + CSV_FILE_NAME);
      return tendersS3Object.getObjectContent();
    } catch (Exception exception) {
      log.error("Exception while downloading the projects data from S3: " + exception.getMessage());
      throw new ResourceNotFoundException("Failed to download oppertunity data. File not found");
    }
  }
}
