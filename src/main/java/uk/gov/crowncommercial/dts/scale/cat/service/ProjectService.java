package uk.gov.crowncommercial.dts.scale.cat.service;

import org.openapitools.model.AgreementDetails;
import org.openapitools.model.DefaultName;
import org.openapitools.model.DraftProcurementProject;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import lombok.RequiredArgsConstructor;

/**
 * Simple service example to fetch data from the Scale shared Agreements Service
 */
@Service
@RequiredArgsConstructor
public class ProjectService {

  private final RestTemplate jaggaerRestTemplate;

  public DraftProcurementProject createProjectFromAgreement(AgreementDetails agreementDetails) {
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
     *
     * - Persist the templateReferenceCode as procurement ID to the Tenders DB against CA/Lot
     *
     * - Invoke EventService.createEvent()
     *
     */

    return draftProcurementProject;
  }

}
