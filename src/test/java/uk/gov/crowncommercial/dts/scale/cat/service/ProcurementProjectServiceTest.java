package uk.gov.crowncommercial.dts.scale.cat.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.web.reactive.function.client.WebClient;
import uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementProject;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.AgreementDetails;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.EventSummary;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.CreateUpdateProject;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.CreateUpdateProjectResponse;
import uk.gov.crowncommercial.dts.scale.cat.repo.ProcurementProjectRepo;
import uk.gov.crowncommercial.dts.scale.cat.utils.TendersAPIModelUtils;

/**
 * Service layer tests
 */
@SpringBootTest(
    classes = {ProcurementProjectService.class, JaggaerAPIConfig.class, TendersAPIModelUtils.class},
    webEnvironment = WebEnvironment.NONE)
@EnableConfigurationProperties(JaggaerAPIConfig.class)
class ProcurementProjectServiceTest {

  private static final String PRINCIPAL = "jsmith@ccs.org.uk";
  private static final String JAGGAER_USER_ID = "12345";
  private static final String TENDER_REF_CODE = "project_0001";
  private static final String CA_NUMBER = "RM1234";
  private static final String LOT_NUMBER = "Lot1a";
  private static final Integer PROC_PROJECT_ID = 1;

  @MockBean(answer = Answers.RETURNS_DEEP_STUBS)
  private WebClient jaggaerWebClient;

  @MockBean
  private JaggaerUserProfileService jaggaerUserProfileService;

  @MockBean
  private ProcurementProjectRepo procurementProjectRepo;

  @MockBean
  private ProcurementEventService procurementEventService;

  @Autowired
  private JaggaerAPIConfig jaggaerAPIConfig;

  @Autowired
  private ProcurementProjectService procurementProjectService;

  @Test
  void testCreateFromAgreementDetails() throws Exception {

    // Stub some objects
    var agreementDetails = new AgreementDetails();
    agreementDetails.setAgreementID(CA_NUMBER);
    agreementDetails.setLotID(LOT_NUMBER);

    var createUpdateProjectResponse = new CreateUpdateProjectResponse();
    createUpdateProjectResponse.setReturnCode(0);
    createUpdateProjectResponse.setReturnMessage("OK");
    createUpdateProjectResponse.setTenderReferenceCode(TENDER_REF_CODE);

    var procurementProject = ProcurementProject.of(agreementDetails, TENDER_REF_CODE, PRINCIPAL);
    var eventSummary = new EventSummary();

    // Mock behaviours
    when(jaggaerUserProfileService.resolveJaggaerUserId(PRINCIPAL)).thenReturn(JAGGAER_USER_ID);
    when(jaggaerWebClient.post().uri(jaggaerAPIConfig.getCreateProject().get("endpoint"))
        .bodyValue(any(CreateUpdateProject.class)).retrieve()
        .bodyToMono(eq(CreateUpdateProjectResponse.class))
        .block(Duration.ofSeconds(jaggaerAPIConfig.getTimeoutDuration())))
            .thenReturn(createUpdateProjectResponse);

    when(procurementProjectRepo.save(any(ProcurementProject.class))).then(mock -> {
      procurementProject.setId(PROC_PROJECT_ID);
      return procurementProject;
    });

    when(procurementEventService.createFromAgreementDetails(PROC_PROJECT_ID, PRINCIPAL))
        .thenReturn(eventSummary);

    // Invoke
    var draftProcurementProject =
        procurementProjectService.createFromAgreementDetails(agreementDetails, PRINCIPAL);

    // Verify & assert
    assertEquals(CA_NUMBER + '-' + LOT_NUMBER + "-CCS",
        draftProcurementProject.getDefaultName().getName());
  }

}
