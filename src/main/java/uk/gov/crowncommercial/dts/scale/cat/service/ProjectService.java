package uk.gov.crowncommercial.dts.scale.cat.service;

import java.time.Instant;
import java.util.UUID;
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
public class ProjectService {

  private final JaggaerAPIConfig jaggaerAPIConfig;
  private final WebClient jaggaerWebClient;
  private final JaggaerUserProfileService jaggaerUserProfileService;
  private final ProcurementProjectRepo procurementProjectRepo;

  public DraftProcurementProject createProjectFromAgreement(AgreementDetails agreementDetails,
      String principal) {

    String jaggaerUserId = jaggaerUserProfileService.resolveJaggaerUserId(principal);
    String projectTitle = getDefaultProjectTitle(agreementDetails, "CCS");

    /*
     * TODO:
     *
     * - Get user's Jaggaer ID and organisation from Jaggaer profile based on OIDC login id (from
     * validated JWT via Spring Sec)
     *
     * - Query the Tenders DB for Jaggaer project template based on the incoming CA and Lot
     *
     * - Construct and call Jaggaer create project endpoint (with/without sourceTemplateCode)
     */

    CreateUpdateProject createUpdateProject = new CreateUpdateProject(OperationCode.CREATEUPDATE,
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
    final UUID procurementProjectUUID = UUID.randomUUID();
    ProcurementProject procurementProject = new ProcurementProject();
    procurementProject.setId(procurementProjectUUID);
    procurementProject.setCaNumber(agreementDetails.getAgreementID());
    procurementProject.setLotNumber(agreementDetails.getLotID());
    procurementProject.setJaggaerProjectId(createProjectResponse.getTenderReferenceCode());
    procurementProject.setCreatedBy(principal); // Or Jaggaer user ID?
    procurementProject.setCreatedAt(Instant.now());

    /*
     * Invoke EventService.createEvent()
     */

    DraftProcurementProject draftProcurementProject = new DraftProcurementProject();
    // draftProcurementProject.setPocurementID(procurementProjectUUID);
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
