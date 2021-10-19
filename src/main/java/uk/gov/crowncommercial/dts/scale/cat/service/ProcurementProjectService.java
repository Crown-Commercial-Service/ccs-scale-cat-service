package uk.gov.crowncommercial.dts.scale.cat.service;

import static java.util.Optional.ofNullable;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig.ENDPOINT;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.web.reactive.function.client.WebClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.config.AgreementsServiceAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.exception.JaggaerApplicationException;
import uk.gov.crowncommercial.dts.scale.cat.exception.ResourceNotFoundException;
import uk.gov.crowncommercial.dts.scale.cat.model.EventType;
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
  private final RetryableTendersDBDelegate retryableTendersDBDelegate;
  private final ProcurementEventService procurementEventService;
  private final TendersAPIModelUtils tendersAPIModelUtils;
  private final AgreementsServiceAPIConfig agreementsServiceAPIConfig;
  private final WebClient agreementsServiceWebClient;
  private final ObjectMapper mapper;

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
  public DraftProcurementProject createFromAgreementDetails(AgreementDetails agreementDetails,
      String principal) {

    // Fetch Jaggaer ID and Buyer company ID from Jaggaer profile based on OIDC login id
    var jaggaerUserId = userProfileService.resolveJaggaerUserId(principal);
    var jaggaerBuyerCompanyId = userProfileService.resolveJaggaerBuyerCompanyId(principal);
    var projectTitle = getDefaultProjectTitle(agreementDetails, "CCS");

    var tender = Tender.builder().title(projectTitle)
        .buyerCompany(BuyerCompany.builder().id(jaggaerBuyerCompanyId).build())
        .projectOwner(new ProjectOwner(jaggaerUserId))
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

  String getDefaultProjectTitle(AgreementDetails agreementDetails, String organisation) {
    return String.format(jaggaerAPIConfig.getCreateProject().get("defaultTitleFormat"),
        agreementDetails.getAgreementId(), agreementDetails.getLotId(), organisation);
  }

  /**
   * Update project name.
   * @param projectId The Project Id
   * @param projectName The Project Name
   * @param principal The Principal
   */
  public void updateProcurementProjectName(final Integer projectId, final String projectName,
      final String principal) {

    Assert.hasLength(projectName, "New project name must be supplied");

    ProcurementProject project = retryableTendersDBDelegate.findProcurementProjectById(projectId)
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
   * @param projectId the project id
   * @return Collection of event types
   */
  public Collection<EventType> getEventTypes(final Integer projectId){

    final ProcurementProject project = retryableTendersDBDelegate.findProcurementProjectById(projectId)
            .orElseThrow(() -> new ResourceNotFoundException("Project '" + projectId + "' not found"));

    var eventTypes =
            ofNullable(agreementsServiceWebClient.get()
                            .uri(agreementsServiceAPIConfig.getGetEventTypesForAgreement().get("uriTemplate"), project.getCaNumber(),project.getLotNumber())
                            .retrieve()
                            .bodyToMono(Object[].class)
                            .block(Duration.ofSeconds(agreementsServiceAPIConfig.getTimeoutDuration()))
            )
                    .orElseThrow(() -> new ResourceNotFoundException("Unexpected error finding event types"));

    return  Arrays.stream(eventTypes).map(object -> mapper.convertValue(object, EventType.class))
            .collect(Collectors.toList());
  }
}
