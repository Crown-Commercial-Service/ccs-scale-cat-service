package uk.gov.crowncommercial.dts.scale.cat.service;

import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.AgreementDetails;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.DefaultName;
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

    DraftProcurementProject draftProcurementProject = new DraftProcurementProject();
    DefaultName defaultName = new DefaultName();
    defaultName.setName(getDefaultProjectTitle(agreementDetails, "CCS"));
    draftProcurementProject.setDefaultName(defaultName);

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

    CreateUpdateProject createUpdateProject =
        new CreateUpdateProject(OperationCode.CREATE_FROM_TEMPLATE,
            new Project(Tender.builder().title(getDefaultProjectTitle(agreementDetails, "CCS"))
                .buyerCompany(new BuyerCompany("52423"))
                .projectOwner(new ProjectOwner(jaggaerUserId)).build()));

    /*
     * - Persist the templateReferenceCode as procurement ID to the Tenders DB against CA/Lot
     *
     * - Invoke EventService.createEvent()
     *
     */

    Map<String, Object> createProjectResult =
        jaggaerWebClient.post().uri(jaggaerAPIConfig.getCreateProject().get("endpoint"))
            .bodyValue(createUpdateProject).retrieve().bodyToMono(Map.class).block();

    log.info("Created project: " + createProjectResult);

    return draftProcurementProject;
  }

  String getDefaultProjectTitle(AgreementDetails agreementDetails, String organisation) {
    return String.format(jaggaerAPIConfig.getCreateProject().get("defaultTitleFormat"),
        agreementDetails.getAgreementID(), agreementDetails.getLotID(), organisation);
  }

}
