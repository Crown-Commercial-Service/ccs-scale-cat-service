package uk.gov.crowncommercial.dts.scale.cat.service;

import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

  private final WebClient jaggaerWebClient;

  public DraftProcurementProject createProjectFromAgreement(AgreementDetails agreementDetails,
      String jaggaerUserId) {

    DraftProcurementProject draftProcurementProject = new DraftProcurementProject();
    DefaultName defaultName = new DefaultName();
    defaultName.setName("CA123-Lot123-Org123");
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
            new Project(
                Tender.builder().title("CA-LOT-TODO").buyerCompany(new BuyerCompany("52423"))
                    .projectOwner(new ProjectOwner(jaggaerUserId)).build()));

    /*
     * - Persist the templateReferenceCode as procurement ID to the Tenders DB against CA/Lot
     *
     * - Invoke EventService.createEvent()
     *
     */

    Map<String, Object> result = jaggaerWebClient.get()
        .uri("/esop/jint/api/public/ja/v1/projects/{projectId}", "tender_42433").retrieve()
        .bodyToMono(Map.class).block();

    log.info("PROJECT: " + result);

    return draftProcurementProject;
  }

  String getDefaultProjectTitle(AgreementDetails agreementDetails, String organisation) {
    return "TODO";
  }

}
