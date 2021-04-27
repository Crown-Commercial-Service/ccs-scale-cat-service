package uk.gov.crowncommercial.dts.scale.cat.service;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.AgreementDetails;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.DefaultName;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.DefaultNameComponents;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.DraftProcurementProject;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.*;

/**
 * Simple service example to fetch data from the Scale shared Agreements Service
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectService {

  private final JaggaerAPIConfig jaggaerAPIConfig;
  private final WebClient jaggaerWebClient;

  public DraftProcurementProject createProjectFromAgreement(AgreementDetails agreementDetails,
      String jaggaerUserId) {

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
        new Project(Tender.builder().title(projectTitle).buyerCompany(new BuyerCompany("52423"))
            .projectOwner(new ProjectOwner(jaggaerUserId)).build()));

    /*
     * - Persist the templateReferenceCode as procurement ID to the Tenders DB against CA/Lot
     *
     * - Invoke EventService.createEvent()
     *
     */

    CreateUpdateProjectResponse createProjectResponse = jaggaerWebClient.post()
        .uri(jaggaerAPIConfig.getCreateProject().get("endpoint")).bodyValue(createUpdateProject)
        .retrieve().bodyToMono(CreateUpdateProjectResponse.class).block();

    if (createProjectResponse.getReturnCode() != 0
        || !createProjectResponse.getReturnMessage().equals("OK")) {
      log.error(createProjectResponse.toString());
      throw new RuntimeException("TODO: Error creating project");
    }
    log.info("Created project: {}", createProjectResponse);

    DraftProcurementProject draftProcurementProject = new DraftProcurementProject();
    DefaultName defaultName = new DefaultName();
    defaultName.setName(projectTitle);
    DefaultNameComponents defaultNameComps = new DefaultNameComponents();
    defaultNameComps.setAgreement(agreementDetails.getAgreementID());
    defaultNameComps.setOrg("CCS");
    draftProcurementProject.setDefaultName(defaultName);

    return draftProcurementProject;
  }

  String getDefaultProjectTitle(AgreementDetails agreementDetails, String organisation) {
    return String.format(jaggaerAPIConfig.getCreateProject().get("defaultTitleFormat"),
        agreementDetails.getAgreementID(), agreementDetails.getLotID(), organisation);
  }

}
