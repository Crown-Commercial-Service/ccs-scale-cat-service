package uk.gov.crowncommercial.dts.scale.cat.service;

import java.time.Duration;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.exception.JaggaerApplicationException;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementProject;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.AgreementDetails;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.DraftProcurementProject;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.*;
import uk.gov.crowncommercial.dts.scale.cat.repo.ProcurementProjectRepo;
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
  private final ProcurementProjectRepo procurementProjectRepo;
  private final ProcurementEventService procurementEventService;
  private final TendersAPIModelUtils tendersAPIModelUtils;

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

    // Fetch Jaggaer ID (and org?) from Jaggaer profile based on OIDC login id
    var jaggaerUserId = userProfileService.resolveJaggaerUserId(principal);
    var projectTitle = getDefaultProjectTitle(agreementDetails, "CCS");

    var createUpdateProject = new CreateUpdateProject(OperationCode.CREATE_FROM_TEMPLATE,
        new Project(Tender.builder().title(projectTitle).buyerCompany(new BuyerCompany("51435"))
            .projectOwner(new ProjectOwner(jaggaerUserId))
            .sourceTemplateReferenceCode(jaggaerAPIConfig.getCreateProject().get("templateId"))
            .build()));

    CreateUpdateProjectResponse createProjectResponse =
        jaggaerWebClient.post().uri(jaggaerAPIConfig.getCreateProject().get("endpoint"))
            .bodyValue(createUpdateProject).retrieve().bodyToMono(CreateUpdateProjectResponse.class)
            .block(Duration.ofSeconds(jaggaerAPIConfig.getTimeoutDuration()));

    if (createProjectResponse.getReturnCode() != 0
        || !"OK".equals(createProjectResponse.getReturnMessage())) {
      throw new JaggaerApplicationException(createProjectResponse.getReturnCode(),
          createProjectResponse.getReturnMessage());
    }
    log.info("Created project: {}", createProjectResponse);

    var procurementProject = procurementProjectRepo.save(ProcurementProject.of(agreementDetails,
        createProjectResponse.getTenderReferenceCode(), projectTitle, principal));

    var eventSummary =
        procurementEventService.createFromAgreementDetails(procurementProject.getId(), principal);

    return tendersAPIModelUtils.buildDraftProcurementProject(agreementDetails,
        procurementProject.getId(), eventSummary.getEventID(), projectTitle);
  }

  String getDefaultProjectTitle(AgreementDetails agreementDetails, String organisation) {
    return String.format(jaggaerAPIConfig.getCreateProject().get("defaultTitleFormat"),
        agreementDetails.getAgreementID(), agreementDetails.getLotID(), organisation);
  }

}
