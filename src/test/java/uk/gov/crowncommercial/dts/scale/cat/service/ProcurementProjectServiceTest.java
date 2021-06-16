package uk.gov.crowncommercial.dts.scale.cat.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.web.reactive.function.client.WebClient;
import uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.exception.JaggaerApplicationException;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementProject;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.AgreementDetails;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.EventSummary;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.CreateUpdateProject;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.CreateUpdateProjectResponse;
import uk.gov.crowncommercial.dts.scale.cat.repo.ProcurementEventRepo;
import uk.gov.crowncommercial.dts.scale.cat.repo.ProcurementProjectRepo;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;
import uk.gov.crowncommercial.dts.scale.cat.utils.TendersAPIModelUtils;

/**
 * Service layer tests
 */
@SpringBootTest(classes = {ProcurementProjectService.class, JaggaerAPIConfig.class,
    TendersAPIModelUtils.class, RetryableTendersDBDelegate.class},
    webEnvironment = WebEnvironment.NONE)
@EnableConfigurationProperties(JaggaerAPIConfig.class)
class ProcurementProjectServiceTest {

  private static final String PRINCIPAL = "jsmith@ccs.org.uk";
  private static final String JAGGAER_USER_ID = "12345";
  private static final String TENDER_CODE = "tender_0001";
  private static final String TENDER_REF_CODE = "project_0001";
  private static final String CA_NUMBER = "RM1234";
  private static final String LOT_NUMBER = "Lot1a";
  private static final String ORG = "CCS";
  private static final String PROJ_NAME = CA_NUMBER + '-' + LOT_NUMBER + '-' + ORG;
  private static final String EVENT_OCID = "ocds-abc123-1";
  private static final Integer PROC_PROJECT_ID = 1;

  private final AgreementDetails agreementDetails = new AgreementDetails();

  @MockBean(answer = Answers.RETURNS_DEEP_STUBS)
  private WebClient jaggaerWebClient;

  @MockBean
  private UserProfileService userProfileService;

  @MockBean
  private ProcurementProjectRepo procurementProjectRepo;

  @MockBean
  private ProcurementEventRepo procurementEventRepo;

  @MockBean
  private ProcurementEventService procurementEventService;

  @Autowired
  private JaggaerAPIConfig jaggaerAPIConfig;

  @Autowired
  private ProcurementProjectService procurementProjectService;

  @BeforeEach
  void beforeEach() {
    agreementDetails.setAgreementID(CA_NUMBER);
    agreementDetails.setLotID(LOT_NUMBER);
  }

  @Test
  void testCreateFromAgreementDetails() throws Exception {

    // Stub some objects
    var createUpdateProjectResponse = new CreateUpdateProjectResponse();
    createUpdateProjectResponse.setReturnCode(0);
    createUpdateProjectResponse.setReturnMessage("OK");
    createUpdateProjectResponse.setTenderCode(TENDER_CODE);
    createUpdateProjectResponse.setTenderReferenceCode(TENDER_REF_CODE);

    var procurementProject =
        ProcurementProject.of(agreementDetails, TENDER_CODE, TENDER_REF_CODE, PROJ_NAME, PRINCIPAL);
    procurementProject.setId(PROC_PROJECT_ID);

    var eventSummary = new EventSummary();
    eventSummary.setEventID(EVENT_OCID);

    // Mock behaviours
    when(userProfileService.resolveJaggaerUserId(PRINCIPAL)).thenReturn(JAGGAER_USER_ID);
    when(jaggaerWebClient.post().uri(jaggaerAPIConfig.getCreateProject().get("endpoint"))
        .bodyValue(any(CreateUpdateProject.class)).retrieve()
        .bodyToMono(eq(CreateUpdateProjectResponse.class))
        .block(Duration.ofSeconds(jaggaerAPIConfig.getTimeoutDuration())))
            .thenReturn(createUpdateProjectResponse);
    when(procurementProjectRepo.save(any(ProcurementProject.class))).thenReturn(procurementProject);
    when(procurementEventService.createFromProject(PROC_PROJECT_ID, PRINCIPAL))
        .thenReturn(eventSummary);

    // Invoke
    var draftProcurementProject =
        procurementProjectService.createFromAgreementDetails(agreementDetails, PRINCIPAL);

    // Assert
    assertEquals(PROC_PROJECT_ID, draftProcurementProject.getPocurementID());
    assertEquals(EVENT_OCID, draftProcurementProject.getEventID());
    assertEquals(CA_NUMBER + '-' + LOT_NUMBER + '-' + ORG,
        draftProcurementProject.getDefaultName().getName());
    assertEquals(CA_NUMBER,
        draftProcurementProject.getDefaultName().getComponents().getAgreementID());
    assertEquals(LOT_NUMBER, draftProcurementProject.getDefaultName().getComponents().getLotID());
    assertEquals(ORG, draftProcurementProject.getDefaultName().getComponents().getOrg());

    // Verify
    verify(userProfileService).resolveJaggaerUserId(PRINCIPAL);
    verify(procurementProjectRepo).save(any(ProcurementProject.class));
    // verify(jaggaerWebClient.post().uri(anyString()).bodyValue(any(CreateUpdateProject.class))
    // .retrieve().bodyToMono(eq(CreateUpdateProjectResponse.class))).block(any());
    verify(procurementEventService).createFromProject(PROC_PROJECT_ID, PRINCIPAL);
  }

  @Test
  void testCreateFromAgreementDetailsThrowsJaggaerApplicationException() throws Exception {
    // Stub some objects
    var jaggaerErrorResponse = new CreateUpdateProjectResponse();
    jaggaerErrorResponse.setReturnCode(1);
    jaggaerErrorResponse.setReturnMessage("NOT OK");

    // Mock behaviours
    when(userProfileService.resolveJaggaerUserId(PRINCIPAL)).thenReturn(JAGGAER_USER_ID);
    when(jaggaerWebClient.post().uri(jaggaerAPIConfig.getCreateProject().get("endpoint"))
        .bodyValue(any(CreateUpdateProject.class)).retrieve()
        .bodyToMono(eq(CreateUpdateProjectResponse.class))
        .block(Duration.ofSeconds(jaggaerAPIConfig.getTimeoutDuration())))
            .thenReturn(jaggaerErrorResponse);

    // Invoke & assert
    JaggaerApplicationException jagEx = assertThrows(JaggaerApplicationException.class,
        () -> procurementProjectService.createFromAgreementDetails(agreementDetails, PRINCIPAL));
    assertEquals("Jaggaer application exception, Code: [1], Message: [NOT OK]", jagEx.getMessage());
  }

}
