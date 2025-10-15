package uk.gov.crowncommercial.dts.scale.cat.service;

import static java.util.Comparator.comparing;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toCollection;
import static org.opensearch.index.query.QueryBuilders.boolQuery;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig.ENDPOINT;
import static uk.gov.crowncommercial.dts.scale.cat.model.entity.Timestamps.createTimestamps;
import static uk.gov.crowncommercial.dts.scale.cat.service.scheduler.ProjectsCSVGenerationScheduledTask.CSV_FILE_NAME;
import static uk.gov.crowncommercial.dts.scale.cat.service.scheduler.ProjectsCSVGenerationScheduledTask.CSV_FILE_PREFIX;
import static uk.gov.crowncommercial.dts.scale.cat.utils.TendersAPIModelUtils.getInstantFromDate;
import static uk.gov.crowncommercial.dts.scale.cat.utils.TendersAPIModelUtils.getTenderPeriod;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.modelmapper.ModelMapper;
import org.odftoolkit.simple.SpreadsheetDocument;
import org.odftoolkit.simple.table.Table;
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
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
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
@EnableScheduling
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
  private final S3Client tendersS3Client;
  private final AWSS3Service tendersS3Service;


  private static final String PROJECT_NAME = "projectName";
  private static final String PROJECT_DESCRIPTION = "description";
  private static final String LOT = "lot";

  private static final String STATUS = "status";

  private static final String COUNT_AGGREGATION = "count_lot";
  private static final String SEARCH_URI = "/tenders/projects/search?agreement-id=RM1043.8&keyword=%s&page=%s&page-size=%s";

  private final Map<String, List<ProcurementEventSearch>> projectCache = new ConcurrentHashMap<>();
  private static final String ALL_RESULTS_CACHE_KEY = "ALL_RESULTS";
  // Simple CSV split that respects quoted commas (not a full CSV parser, but handles typical quoted fields)
  private static final Pattern CSV_SPLIT_REGEX = Pattern.compile(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");


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

    // Build a quick lookup of active (not deleted) mappings
    var activeRoleByUserId = existingList.stream()
      .filter(pum -> !pum.isDeleted())
      .collect(Collectors.toMap(ProjectUserMapping::getUserId, pum -> pum, (a,b) -> a));

    var finalTeamIds =
        teamIds.stream().filter(e -> existingList.stream().filter(ProjectUserMapping::isDeleted)
            .noneMatch(k -> e.equals(k.getUserId()))).collect(Collectors.toSet());
    combinedIds.addAll(finalTeamIds);

    // Retrieve additional info on each user from Jaggaer and Conclave
    return combinedIds.stream()
      .map(i -> {
        var teamMember = getTeamMember(i, finalTeamIds, emailRecipientIds, projectOwner.getId());
        if (teamMember != null) {
          var nonOcds = teamMember.getNonOCDS();
          var role = activeRoleByUserId.get(i);
          if (role != null && nonOcds != null) {
            nonOcds.setCollaborator(role.isCollaborator());
            nonOcds.setAssessor(role.isAssessor());
            nonOcds.setModerator(role.isModerator());
          } else if (nonOcds != null) {
            // Legacy users with no stored role: all three remain false (default)
            nonOcds.setCollaborator(false);
            nonOcds.setAssessor(false);
            nonOcds.setModerator(false);
          }
        }
        return teamMember;
      })
      .filter(Objects::nonNull)
      .collect(Collectors.toSet());
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
      case COLLABORATOR:
      case ASSESSOR:
      case MODERATOR:
        log.debug("{} update", updateTeamMember.getUserType());
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

    // In the database, enable exactly one of our roles
    updateDbRoleFlags(dbProject, jaggaerUserId, updateTeamMember, principal);

    return getProjectTeamMembers(projectId, principal).stream()
        .filter(tm -> tm.getOCDS().getId().equalsIgnoreCase(userId)).findFirst().orElseThrow();
  }

  private void updateDbRoleFlags(final ProcurementProject project, final String jaggaerUserId, final UpdateTeamMember updateTeamMember, final String principal) {
    var maybeMapping = retryableTendersDBDelegate
        .findProjectUserMappingByProjectIdAndUserId(project.getId(), jaggaerUserId);

    var mapping = maybeMapping.orElseGet(() ->
        ProjectUserMapping.builder()
            .project(project)
            .userId(jaggaerUserId)
            .timestamps(createTimestamps(principal))
            .build());

    // Always undelete when setting a role
    mapping.setDeleted(false);

    switch (updateTeamMember.getUserType()) {
      case COLLABORATOR:
        mapping.setCollaborator(true);
        mapping.setAssessor(false);
        mapping.setModerator(false);
        break;
      case ASSESSOR:
        mapping.setCollaborator(false);
        mapping.setAssessor(true);
        mapping.setModerator(false);
        break;
      case MODERATOR:
        mapping.setCollaborator(false);
        mapping.setAssessor(false);
        mapping.setModerator(true);
        break;
      case TEAM_MEMBER:
      case PROJECT_OWNER:
        // Clear API-local flags: primary role is plain team-member or owner
        mapping.setCollaborator(false);
        mapping.setAssessor(false);
        mapping.setModerator(false);
        break;
      case EMAIL_RECIPIENT:
        // Orthogonal; don't touch local role flags
        break;
      default:
        // no-op
    }

    // Touch timestamps when updating an existing mapping
    mapping.setTimestamps(
        mapping.getTimestamps() == null
            ? createTimestamps(principal)
            : Timestamps.updateTimestamps(mapping.getTimestamps(), principal));

    retryableTendersDBDelegate.save(mapping);
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
  public ProjectPackageSummary getProjectSummary(final String principal, final Integer projectId, final boolean hasAdminAccess) {
    // Grab the Jaggaer details we need to start from the authentication
    String userId = userProfileService.resolveBuyerUserProfile(principal).orElseThrow(() -> new AuthorisationFailureException("Jaggaer user not found")).getUserId();

    ProjectPackageSummary mappedProject = null;

    if (hasAdminAccess) {
      // This is an admin user - we need to lookup by the project ID without worrying about the owning user
      Set<ProjectUserMapping> foundProjects = retryableTendersDBDelegate.findProjectUserMappingByProjectId(projectId);

      mappedProject = convertProjectUserMappingsToProjectPackageSummary(foundProjects.stream().toList(), projectId);
    } else if (userId != null && !userId.isEmpty()) {
      // This isn't an admin - so we want to fetch the projects mapped against this user from our Tenders DB
      List<ProjectUserMapping> userProjects = retryableTendersDBDelegate.findProjectUserMappingByUserId(userId,null,null, PageRequest.of(0, 20, Sort.by("project.procurementEvents.updatedAt").descending()));

      mappedProject = convertProjectUserMappingsToProjectPackageSummary(userProjects, projectId);
    }

    if (mappedProject != null) {
      return mappedProject;
    } else {
      throw new ResourceNotFoundException("Project '" + projectId + "' not found");
    }
  }

  /**
   * Takes a set of ProjectUserMappings and converts to a single ProjectPackageSummary
   */
  private ProjectPackageSummary convertProjectUserMappingsToProjectPackageSummary(List<ProjectUserMapping> projects, Integer projectId) {
    if (projects != null && !projects.isEmpty()) {
      // Now we need to filter down to only include the project we're after
      ProjectUserMapping projectMapping = projects.stream()
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

    // We either couldn't find or map the relevant project, so return null
    return null;
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

    // Set user role based on ProjectUserMapping flags matched
    ProjectPackageSummary.UserRoleEnum userRole = getUserRoleFromMapping(mapping);
    projectPackageSummary.setUserRole(userRole);

    return Optional.of(projectPackageSummary);
  }

  public void deleteProject(final Integer projectId, final String principal) {
    log.debug("delete Project with Project ID: {}", projectId);

    // First find given project, to check if it has any active events
    // This is an admin user - we need to lookup by the project ID without worrying about the owning user
    Set<ProjectUserMapping> foundProjects = retryableTendersDBDelegate.findProjectUserMappingByProjectId(projectId);
    List<ProjectUserMapping> projects = foundProjects.stream().toList();

    // Now we need to filter down to only include the project we're checking for in our potential list of projects
    ProjectUserMapping projectMapping = projects.stream().filter(p -> p.getProject().getId().equals(projectId)).findFirst().orElse(null);

    // If project has successfully been found, we can continue with the delete request
    if (projectMapping != null) {
        // Get a list of any events for the found project
        Set<String> externalEventIds = projectMapping.getProject().getProcurementEvents().stream().map(ProcurementEvent::getExternalEventId).collect(Collectors.toSet());

        // If no events have been found, we can continue with the delete request
        if (externalEventIds.isEmpty()) {
            // Delete Table Data 1 (ProjectUserMapping)
            retryableTendersDBDelegate.deleteProjectUserMappingByProjectId(projectId);

            // Delete Table Data 2 (ProcurementProject)
            retryableTendersDBDelegate.deleteProcurementProjectById(projectId);
        } else {
            throw new IllegalArgumentException("Cannot Delete: This project has active events against it. Please delete these events in Jaggaer and  the CaS API, to proceed.");
        }
    } else {
        throw new ResourceNotFoundException("Cannot Delete: Project could not be found.");
    }
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
          .orElseThrow(() -> new ResourceNotFoundException(""));
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
    eventsList.sort(comparing(ProcurementEvent::getUpdatedAt).reversed());

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

  /**
   * Refresh the cache every 24 hours (86400000 ms).
   */
  @Scheduled(fixedRate = 86_400_000)
  public void refreshCacheDaily() {
    log.info("Scheduled refresh of ProcurementEventSearch cache started...");
    fetchAllFromElasticsearch();
    log.info("Scheduled refresh of ProcurementEventSearch cache completed.");
  }

  /**
   * Load all procurement events from Elasticsearch into cache (one-time bulk load).
   */
  public synchronized void fetchAllFromElasticsearch() {
    log.info("Fetching all ProcurementEventSearch records from Elasticsearch into cache...");
    // Using a large page size to fetch everything, adjust if dataset is very large
    NativeSearchQuery query = new NativeSearchQueryBuilder()
            .withPageable(PageRequest.of(0, 10000)) // adjust batch size if needed
            .build();

    SearchHits<ProcurementEventSearch> hits =
            elasticsearchOperations.search(query, ProcurementEventSearch.class);

    List<ProcurementEventSearch> allResults = hits.stream()
            .map(SearchHit::getContent)
            .collect(Collectors.toList());

    projectCache.put(ALL_RESULTS_CACHE_KEY, allResults);
    log.info("Cached {} ProcurementEventSearch records", allResults.size());
  }

  /**
   * Return cached results, or fetch fresh if cache is empty.
   */
  private List<ProcurementEventSearch> getCachedResults() {
    if (!projectCache.containsKey(ALL_RESULTS_CACHE_KEY)) {
      fetchAllFromElasticsearch();
    }
    return projectCache.get(ALL_RESULTS_CACHE_KEY);
  }

  /**
   * Returns a model representing publicly available project data
   */
  public ProjectPublicSearchResult getProjectSummery(final String keyword, final String lotId, int page, int pageSize, ProjectFilters projectFilters) {

    List<ProcurementEventSearch> allResults = getCachedResults();

    // Apply filters in-memory
    Stream<ProcurementEventSearch> stream = allResults.stream();

    if (lotId != null && !lotId.isEmpty()) {
      stream = stream.filter(p -> lotId.equalsIgnoreCase(p.getLot()));
    }

    if (projectFilters != null && !CollectionUtils.isEmpty(projectFilters.getFilters())) {
      List<String> selectedStatuses = projectFilters.getFilters().stream()
              .filter(f -> "status".equalsIgnoreCase(f.getName()))
              .flatMap(f -> f.getOptions().stream())
              .filter(o -> Boolean.TRUE.equals(o.getSelected()))
              .map(o -> o.getText())
              .toList();

      if (!selectedStatuses.isEmpty()) {
        stream = stream.filter(p -> selectedStatuses.contains(p.getStatus()));
      }
    }

    if (keyword != null && !keyword.isBlank()) {
      String lowerKeyword = keyword.toLowerCase();
      stream = stream.filter(p ->
              (p.getProjectName() != null && p.getProjectName().toLowerCase().contains(lowerKeyword)) ||
                      (p.getDescription() != null && p.getDescription().toLowerCase().contains(lowerKeyword))
      );
    }

    List<ProcurementEventSearch> filtered = stream.toList();

    // DEDUPLICATE HERE - BEFORE PAGINATION
    List<ProcurementEventSearch> deduplicated = filtered.stream()
        .collect(collectingAndThen(
            toCollection(() -> new TreeSet<>(comparing(ProcurementEventSearch::getProjectId))),
            ArrayList::new));

    // Pagination on deduplicated results
    int fromIndex = Math.max((page - 1) * pageSize, 0);
    int toIndex = Math.min(fromIndex + pageSize, deduplicated.size());
    List<ProcurementEventSearch> pageResults =
            fromIndex > deduplicated.size() ? Collections.emptyList() : deduplicated.subList(fromIndex, toIndex);

    // Build response
    ProjectPublicSearchResult result = new ProjectPublicSearchResult();
    ProjectSearchCriteria searchCriteria = new ProjectSearchCriteria();
    searchCriteria.setKeyword(keyword);
    searchCriteria.setFilters(projectFilters != null ? projectFilters.getFilters() : null);

    result.setSearchCriteria(searchCriteria);
    result.setResults(convertResultsFromCache(pageResults));
    result.setTotalResults(deduplicated.size());
    result.setLinks(generateLinks(keyword, page, pageSize, deduplicated.size()));

    return result;
  }

  /**
   * Convert cached ProcurementEventSearch list to summaries.
   */
  private List<ProjectPublicSearchSummary> convertResultsFromCache(List<ProcurementEventSearch> results) {
    // Just convert, no deduplication needed since it's handled before pagination
    return results.stream().map(object -> {
      ProjectPublicSearchSummary summary = new ProjectPublicSearchSummary();
      summary.setProjectId(object.getProjectId());
      summary.setProjectName(object.getProjectName());
      summary.setAgreement(object.getAgreement());
      summary.setBudgetRange(object.getBudgetRange());
      summary.setDescription(object.getDescription());
      summary.setBuyerName(object.getBuyerName());
      summary.setLot(object.getLot());
      summary.setLotName(object.getLotDescription());
      summary.setStatus(ProjectPublicSearchSummary.StatusEnum.fromValue(object.getStatus()));
      summary.setSubStatus(object.getSubStatus());
      summary.setLocation(object.getLocation());
      return summary;
    }).toList();
  }

  /**
   * Asynchronously perform a search query request based on parameters passed in
   */
  @Async
  public CompletableFuture<SearchHits<ProcurementEventSearch>> performProcurementEventSearch(final String keyword, final String lotId, int page, int pageSize, ProjectFilters projectFilters) {
    NativeSearchQuery searchQuery = getSearchQuery(keyword, PageRequest.of(page,pageSize), lotId, projectFilters!=null ? projectFilters.getFilters().stream().findFirst().get() : null);
    SearchHits<ProcurementEventSearch> model = elasticsearchOperations.search(searchQuery, ProcurementEventSearch.class);

    return CompletableFuture.completedFuture(model);
  }

  /**
   * Asynchronously fetch lot count results based on parameters passed in
   */
  @Async
  public CompletableFuture<SearchHits<ProcurementEventSearch>> performProcurementEventResultsCount(final String keyword, final String lotId, ProjectFilters projectFilters) {
    NativeSearchQuery searchCountQuery = getLotCount(keyword,lotId, projectFilters!=null ? projectFilters.getFilters().stream().findFirst().get() : null);
    SearchHits<ProcurementEventSearch> model = elasticsearchOperations.search(searchCountQuery, ProcurementEventSearch.class);

    return CompletableFuture.completedFuture(model);
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

    searchQueryBuilder.withPageable(PageRequest.of(pageRequest.getPageNumber()-1, pageRequest.getPageSize(), Sort.by(Sort.Direction.DESC, "_score")));
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
    NativeSearchQueryBuilder searchQueryBuilder = getFilterQuery(null,projectFilter, keyword);
    searchQueryBuilder.withPageable(PageRequest.of(0, (int) searchProjectRepo.count()));
    searchQueryBuilder.withAggregations(AggregationBuilders.terms(COUNT_AGGREGATION).field("lot.raw").size(100));

    NativeSearchQuery searchQuery = searchQueryBuilder.build();

    return searchQuery;
  }

  /**
   * Converts a set of search results from ElastiCache into a de-duped list of projects for return
   */
  private List<ProjectPublicSearchSummary> convertResults(SearchHits<ProcurementEventSearch> results)
  {
    // First, map all the results to our desired output model
    List<ProjectPublicSearchSummary> model = results.stream().map(SearchHit::getContent).map(object -> {
      ProjectPublicSearchSummary projectPublicSearchSummary = new ProjectPublicSearchSummary();
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

      return projectPublicSearchSummary;
    }).toList();

    // Before we return the results we need to de-dupe them, as we expect dupes at this point but don't want to return any
    model = model.stream().collect(collectingAndThen(toCollection(() -> new TreeSet<>(comparing(ProjectPublicSearchSummary::getProjectId))), ArrayList::new));

    return model;
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
  public InputStream downloadProjectsData(String fileType) {
    try {
      var getObjectRequest = GetObjectRequest.builder()
          .bucket(tendersS3Service.getCredentials().getBucketName())
          .key(CSV_FILE_PREFIX + CSV_FILE_NAME)
          .build();
      
      var tendersS3Object = tendersS3Client.getObject(getObjectRequest);

      if(fileType != null && fileType.equals("xlsx"))
        return convertCsvToXlsx(tendersS3Object);

      if(fileType != null && fileType.equals("ods"))
        return convertCsvToOds(tendersS3Object);

      return tendersS3Object;
    } catch (Exception exception) {
      log.error("Exception while downloading the projects data from S3: " + exception.getMessage());
      throw new ResourceNotFoundException("Failed to download oppertunity data. File not found");
    }
  }

  protected InputStream convertCsvToXlsx(InputStream csvInputStream) throws IOException {

    try (BufferedReader reader = new BufferedReader(new InputStreamReader(csvInputStream, StandardCharsets.UTF_8));
         CSVParser csvParser = CSVFormat.DEFAULT
                 .withQuote('"')
                 .withIgnoreSurroundingSpaces()
                 .parse(reader);
         Workbook workbook = new XSSFWorkbook()) {

      Sheet sheet = workbook.createSheet("CSV Data");
      int rowIdx = 0;

      for (CSVRecord record : csvParser) {
        Row row = sheet.createRow(rowIdx++);
        for (int i = 0; i < record.size(); i++) {
          Cell cell = row.createCell(i);
          cell.setCellValue(record.get(i));
        }
      }

      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      workbook.write(baos);
      return new ByteArrayInputStream(baos.toByteArray());
    }
  }

  public InputStream convertCsvToOds(InputStream csvInputStream) throws Exception {
    // Read CSV
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(csvInputStream, StandardCharsets.UTF_8))) {

      // Create new ODS document (Simple API)
      SpreadsheetDocument odsDoc = SpreadsheetDocument.newSpreadsheetDocument();
      Table sheet = odsDoc.getSheetByIndex(0);
      sheet.setTableName("CSV Data");

      String line;
      int rowIndex = 0;

      while ((line = reader.readLine()) != null) {
        // split on commas but ignore commas inside quotes
        String[] values = CSV_SPLIT_REGEX.split(line, -1);
        for (int colIndex = 0; colIndex < values.length; colIndex++) {
          String cellValue = values[colIndex].trim();
          // remove surrounding quotes if present
          if (cellValue.length() >= 2 && cellValue.startsWith("\"") && cellValue.endsWith("\"")) {
            cellValue = cellValue.substring(1, cellValue.length() - 1).replace("\"\"", "\"");
          }

          // set cell value via Simple API
          sheet.getCellByPosition(colIndex, rowIndex).setStringValue(cellValue);
        }
        rowIndex++;
      }

      // Write to in-memory byte array and return InputStream
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      odsDoc.save(baos);
      odsDoc.close();

      return new ByteArrayInputStream(baos.toByteArray());
    }
  }

  /**
   * Determines the user's role based on ProjectUserMapping flags.
   * Role priority: Moderator > Assessor > Collaborator > Owner (default)
   *
   * @param projectUserMapping the project user mapping containing role flags
   * @return UserRoleEnum the determined user role
   */
  private ProjectPackageSummary.UserRoleEnum getUserRoleFromMapping(final ProjectUserMapping projectUserMapping) {
    if (projectUserMapping.isModerator()) {
      return ProjectPackageSummary.UserRoleEnum.MODERATOR;
    } else if (projectUserMapping.isAssessor()) {
      return ProjectPackageSummary.UserRoleEnum.ASSESSOR;
    } else if (projectUserMapping.isCollaborator()) {
      return ProjectPackageSummary.UserRoleEnum.COLLABORATOR;
    } else {
      return ProjectPackageSummary.UserRoleEnum.OWNER;
    }
  }
}
