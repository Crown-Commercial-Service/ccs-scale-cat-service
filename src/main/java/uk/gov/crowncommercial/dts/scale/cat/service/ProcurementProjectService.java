package uk.gov.crowncommercial.dts.scale.cat.service;

import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementProject;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.AgreementDetails;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.DefaultName;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.DefaultNameComponents;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.DraftProcurementProject;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.*;
import uk.gov.crowncommercial.dts.scale.cat.repo.ProcurementProjectRepo;

/**
 * Simple service example to fetch data from the Scale shared Agreements Service
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProcurementProjectService {

  private final JaggaerAPIConfig jaggaerAPIConfig;
  private final WebClient jaggaerWebClient;
  private final JaggaerUserProfileService jaggaerUserProfileService;
  private final ProcurementProjectRepo procurementProjectRepo;

  public DraftProcurementProject createFromAgreementDetails(AgreementDetails agreementDetails,
      String principal) {

    // Fetch Jaggaer ID (and org?) from Jaggaer profile based on OIDC login id
    String jaggaerUserId = jaggaerUserProfileService.resolveJaggaerUserId(principal);
    String projectTitle = getDefaultProjectTitle(agreementDetails, "CCS");

    CreateUpdateProject createUpdateProject =
        new CreateUpdateProject(OperationCode.CREATE_FROM_TEMPLATE,
            new Project(Tender.builder().title(projectTitle).buyerCompany(new BuyerCompany("51435"))
                .projectOwner(new ProjectOwner(jaggaerUserId)).build()));

    CreateUpdateProjectResponse createProjectResponse = jaggaerWebClient.post()
        .uri(jaggaerAPIConfig.getCreateProject().get("endpoint")).bodyValue(createUpdateProject)
        .retrieve().bodyToMono(CreateUpdateProjectResponse.class).block();

    if (createProjectResponse.getReturnCode() != 0
        || !createProjectResponse.getReturnMessage().equals("OK")) {
      log.error(createProjectResponse.toString());
      throw new RuntimeException("TODO: Error creating project");
    }
    log.info("Created project: {}", createProjectResponse);

    /*
     * Create ProcurementEvent
     */
    ProcurementProject procurementProject = new ProcurementProject();
    procurementProject.setCaNumber(agreementDetails.getAgreementID());
    procurementProject.setLotNumber(agreementDetails.getLotID());
    procurementProject.setJaggaerProjectId(createProjectResponse.getTenderReferenceCode());
    procurementProject.setCreatedBy(principal); // Or Jaggaer user ID?
    procurementProject.setCreatedAt(Instant.now());

    /*
     * Invoke EventService.createEvent()
     */

    DraftProcurementProject draftProcurementProject = new DraftProcurementProject();
    draftProcurementProject.setPocurementID(procurementProject.getId());
    // draftProcurementProject.setEventID(TODO);

    DefaultNameComponents defaultNameComponents = new DefaultNameComponents();
    defaultNameComponents.setAgreementID(agreementDetails.getAgreementID());
    defaultNameComponents.setLotID(agreementDetails.getLotID());
    defaultNameComponents.setOrg("CCS");

    DefaultName defaultName = new DefaultName();
    defaultName.setName(projectTitle);
    defaultName.setComponents(defaultNameComponents);
    draftProcurementProject.setDefaultName(defaultName);

    // Persist procurement project and event to database
    procurementProjectRepo.save(procurementProject);

    return draftProcurementProject;
  }

  String getDefaultProjectTitle(AgreementDetails agreementDetails, String organisation) {
    return String.format(jaggaerAPIConfig.getCreateProject().get("defaultTitleFormat"),
        agreementDetails.getAgreementID(), agreementDetails.getLotID(), organisation);
  }

}
