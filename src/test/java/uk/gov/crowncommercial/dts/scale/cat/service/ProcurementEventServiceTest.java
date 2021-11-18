package uk.gov.crowncommercial.dts.scale.cat.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.web.reactive.function.client.WebClient;
import uk.gov.crowncommercial.dts.scale.cat.config.ApplicationFlagsConfig;
import uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.config.OcdsConfig;
import uk.gov.crowncommercial.dts.scale.cat.exception.JaggaerApplicationException;
import uk.gov.crowncommercial.dts.scale.cat.exception.ResourceNotFoundException;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementProject;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.*;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.*;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.ReturnSubUser.SubUser;
import uk.gov.crowncommercial.dts.scale.cat.repo.ProcurementEventRepo;
import uk.gov.crowncommercial.dts.scale.cat.repo.ProcurementProjectRepo;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;
import uk.gov.crowncommercial.dts.scale.cat.utils.TendersAPIModelUtils;

/**
 * Service layer tests
 */
@SpringBootTest(classes = {ProcurementEventService.class, JaggaerAPIConfig.class, OcdsConfig.class,
    TendersAPIModelUtils.class, RetryableTendersDBDelegate.class, ValidationService.class,
    ApplicationFlagsConfig.class}, webEnvironment = WebEnvironment.NONE)
@EnableConfigurationProperties(JaggaerAPIConfig.class)
class ProcurementEventServiceTest {

  private static final Integer PROC_PROJECT_ID = 1;
  private static final Integer PROC_EVENT_DB_ID = 2;
  private static final String PROC_EVENT_ID = "ocds-b5fd17-2";
  private static final String PRINCIPAL = "jsmith@ccs.org.uk";
  private static final String JAGGAER_USER_ID = "12345";
  private static final String RFX_ID = "rfq_0001";
  private static final String RFX_REF_CODE = "itt_0001";
  private static final String PROJECT_NAME = "RM1234-Lot1a-CCS";
  private static final String CA_NUMBER = "RM1234";
  private static final String LOT_NUMBER = "Lot1a";
  private static final String OCDS_AUTH_NAME = "ocds";
  private static final String OCID_PREFIX = "b5fd17";
  private static final String UPDATED_EVENT_NAME = "New Name";
  private static final String UPDATED_EVENT_TYPE = "DA";
  private static final String BUYER_COMPANY_BRAVO_ID = "54321";
  private static final DefineEventType EVENT_TYPE = DefineEventType.DA;
  private static final Boolean DOWNSELECTED_SUPPLIERS = true;
  private static final Optional<SubUser> JAGGAER_USER =
      Optional.of(SubUser.builder().userId(JAGGAER_USER_ID).email(PRINCIPAL).build());
  private static final ReturnCompanyInfo BUYER_COMPANY_INFO =
      ReturnCompanyInfo.builder().bravoId(BUYER_COMPANY_BRAVO_ID).build();

  @MockBean(answer = Answers.RETURNS_DEEP_STUBS)
  private WebClient jaggaerWebClient;

  @MockBean
  private UserProfileService userProfileService;

  @MockBean
  private ProcurementProjectRepo procurementProjectRepo;

  @MockBean
  private ProcurementEventRepo procurementEventRepo;

  @Autowired
  private ProcurementEventService procurementEventService;

  @Autowired
  private JaggaerAPIConfig jaggaerAPIConfig;

  private final CreateEvent createEvent = new CreateEvent();

  @Test
  void testCreateFromProject() throws Exception {

    // Stub some objects
    var agreementDetails = new AgreementDetails();
    agreementDetails.agreementId(CA_NUMBER);
    agreementDetails.setLotId(LOT_NUMBER);

    var createUpdateRfxResponse = new CreateUpdateRfxResponse();
    createUpdateRfxResponse.setReturnCode(0);
    createUpdateRfxResponse.setReturnMessage("OK");
    createUpdateRfxResponse.setRfxId(RFX_ID);
    createUpdateRfxResponse.setRfxReferenceCode(RFX_REF_CODE);

    var procurementProject = ProcurementProject.of(agreementDetails, "", "", "", "");
    var procurementEvent = ProcurementEvent.builder().build();

    // Mock behaviours
    when(userProfileService.resolveBuyerUserByEmail(PRINCIPAL)).thenReturn(JAGGAER_USER);
    when(userProfileService.resolveBuyerCompanyByEmail(PRINCIPAL)).thenReturn(BUYER_COMPANY_INFO);
    when(jaggaerWebClient.post().uri(jaggaerAPIConfig.getCreateRfx().get("endpoint"))
        .bodyValue(any(CreateUpdateRfx.class)).retrieve()
        .bodyToMono(eq(CreateUpdateRfxResponse.class))
        .block(Duration.ofSeconds(jaggaerAPIConfig.getTimeoutDuration())))
            .thenReturn(createUpdateRfxResponse);

    when(procurementProjectRepo.findById(PROC_PROJECT_ID)).then(mock -> {
      procurementProject.setId(PROC_PROJECT_ID);
      procurementProject.setProjectName(PROJECT_NAME);
      return Optional.of(procurementProject);
    });

    when(procurementEventRepo.save(any(ProcurementEvent.class))).then(mock -> {
      procurementEvent.setId(PROC_PROJECT_ID);
      procurementEvent.setOcdsAuthorityName(OCDS_AUTH_NAME);
      procurementEvent.setOcidPrefix(OCID_PREFIX);
      return procurementEvent;
    });

    var createEventNonOCDS = new CreateEventNonOCDS();
    createEventNonOCDS.setEventType(DefineEventType.DA);
    createEvent.setNonOCDS(createEventNonOCDS);

    // Invoke
    ArgumentCaptor<ProcurementEvent> captor = ArgumentCaptor.forClass(ProcurementEvent.class);
    var eventStatus = procurementEventService.createEvent(PROC_PROJECT_ID, createEvent,
        DOWNSELECTED_SUPPLIERS, PRINCIPAL);

    // Verify that entity was created as expected
    verify(procurementEventRepo).save(captor.capture());
    var capturedProcEvent = captor.getValue();
    assertEquals(CA_NUMBER + '-' + LOT_NUMBER + "-CCS-DA", capturedProcEvent.getEventName());
    assertEquals(OCID_PREFIX, capturedProcEvent.getOcidPrefix());
    assertEquals(OCDS_AUTH_NAME, capturedProcEvent.getOcdsAuthorityName());
    assertEquals(RFX_ID, capturedProcEvent.getExternalEventId());
    assertEquals(RFX_REF_CODE, capturedProcEvent.getExternalReferenceId());
    assertEquals(PROC_PROJECT_ID, capturedProcEvent.getProject().getId());
    assertEquals(PRINCIPAL, capturedProcEvent.getCreatedBy());
    assertEquals(ViewEventType.DA.getValue(), capturedProcEvent.getEventType());
    assertEquals(DOWNSELECTED_SUPPLIERS, capturedProcEvent.getDownSelectedSuppliers());
    assertNotNull(capturedProcEvent.getCreatedAt());
    assertNotNull(capturedProcEvent.getUpdatedAt());

    // Verify that event ID is generated correctly by entity
    assertEquals(OCDS_AUTH_NAME + "-" + OCID_PREFIX + "-1", procurementEvent.getEventID());

    // Verify that response is correct
    assertEquals(CA_NUMBER + '-' + LOT_NUMBER + "-CCS-DA", eventStatus.getTitle());
    assertEquals(RFX_REF_CODE, eventStatus.getEventSupportId());
    assertEquals(ReleaseTag.TENDER, eventStatus.getEventStage());
    assertEquals(TenderStatus.PLANNING, eventStatus.getStatus());
    assertEquals(ViewEventType.DA, eventStatus.getEventType());
  }

  @Test
  void testCreateFromEventRequest() throws Exception {

    // Stub some objects
    var agreementDetails = new AgreementDetails();
    agreementDetails.agreementId(CA_NUMBER);
    agreementDetails.setLotId(LOT_NUMBER);

    // Test with null values for EventType and DownselectedSuppliers
    var createUpdateRfxResponse = new CreateUpdateRfxResponse();
    createUpdateRfxResponse.setReturnCode(0);
    createUpdateRfxResponse.setReturnMessage("OK");
    createUpdateRfxResponse.setRfxId(RFX_ID);
    createUpdateRfxResponse.setRfxReferenceCode(RFX_REF_CODE);

    var procurementProject = ProcurementProject.of(agreementDetails, "", "", "", "");
    var procurementEvent = ProcurementEvent.builder().build();

    // Mock behaviours
    when(userProfileService.resolveBuyerUserByEmail(PRINCIPAL)).thenReturn(JAGGAER_USER);
    when(userProfileService.resolveBuyerCompanyByEmail(PRINCIPAL)).thenReturn(BUYER_COMPANY_INFO);
    when(jaggaerWebClient.post().uri(jaggaerAPIConfig.getCreateRfx().get("endpoint"))
        .bodyValue(any(CreateUpdateRfx.class)).retrieve()
        .bodyToMono(eq(CreateUpdateRfxResponse.class))
        .block(Duration.ofSeconds(jaggaerAPIConfig.getTimeoutDuration())))
            .thenReturn(createUpdateRfxResponse);

    when(procurementProjectRepo.findById(PROC_PROJECT_ID)).then(mock -> {
      procurementProject.setId(PROC_PROJECT_ID);
      procurementProject.setProjectName(PROJECT_NAME);
      return Optional.of(procurementProject);
    });

    when(procurementEventRepo.save(any(ProcurementEvent.class))).then(mock -> {
      procurementEvent.setId(PROC_PROJECT_ID);
      procurementEvent.setOcdsAuthorityName(OCDS_AUTH_NAME);
      procurementEvent.setOcidPrefix(OCID_PREFIX);
      return procurementEvent;
    });

    // Invoke
    ArgumentCaptor<ProcurementEvent> captor = ArgumentCaptor.forClass(ProcurementEvent.class);
    var eventStatus =
        procurementEventService.createEvent(PROC_PROJECT_ID, createEvent, null, PRINCIPAL);

    // Verify that entity was created as expected
    verify(procurementEventRepo).save(captor.capture());
    assertEquals(CA_NUMBER + '-' + LOT_NUMBER + "-CCS-TBD", captor.getValue().getEventName());
    assertEquals(OCID_PREFIX, captor.getValue().getOcidPrefix());
    assertEquals(OCDS_AUTH_NAME, captor.getValue().getOcdsAuthorityName());
    assertEquals(RFX_ID, captor.getValue().getExternalEventId());
    assertEquals(RFX_REF_CODE, captor.getValue().getExternalReferenceId());
    assertEquals(PROC_PROJECT_ID, captor.getValue().getProject().getId());
    assertEquals(PRINCIPAL, captor.getValue().getCreatedBy());
    assertEquals(ViewEventType.TBD.getValue(), captor.getValue().getEventType());
    assertEquals(false, captor.getValue().getDownSelectedSuppliers());
    assertNotNull(captor.getValue().getCreatedAt());
    assertNotNull(captor.getValue().getUpdatedAt());

    // Verify that event ID is generated correctly by entity
    assertEquals(OCDS_AUTH_NAME + "-" + OCID_PREFIX + "-1", procurementEvent.getEventID());

    // Verify that response is correct
    assertEquals(CA_NUMBER + '-' + LOT_NUMBER + "-CCS-TBD", eventStatus.getTitle());
    assertEquals(RFX_REF_CODE, eventStatus.getEventSupportId());
    assertEquals(ReleaseTag.TENDER, eventStatus.getEventStage());
    assertEquals(TenderStatus.PLANNING, eventStatus.getStatus());
    assertEquals(ViewEventType.TBD, eventStatus.getEventType());
  }

  @Test
  void testUpdateProcurementEventNameAndType() throws Exception {

    // Create test update object
    var updateEvent = new UpdateEvent();
    updateEvent.setName(UPDATED_EVENT_NAME);
    updateEvent.setEventType(EVENT_TYPE);

    var rfxSetting = RfxSetting.builder().shortDescription(UPDATED_EVENT_NAME).build();
    var rfx = Rfx.builder().rfxSetting(rfxSetting).build();
    var updateRfx = new CreateUpdateRfx(OperationCode.UPDATE, rfx);

    // Validate Jaggaer update
    var createUpdateRfxResponse = new CreateUpdateRfxResponse();
    createUpdateRfxResponse.setReturnCode(0);
    createUpdateRfxResponse.setReturnMessage("OK");

    when(userProfileService.resolveBuyerUserByEmail(PRINCIPAL)).thenReturn(JAGGAER_USER);
    when(jaggaerWebClient.post().uri(jaggaerAPIConfig.getCreateRfx().get("endpoint"))
        .bodyValue(argThat(new UpdateEventNameMatcher(updateRfx))).retrieve()
        .bodyToMono(eq(CreateUpdateRfxResponse.class))
        .block(Duration.ofSeconds(jaggaerAPIConfig.getTimeoutDuration())))
            .thenReturn(createUpdateRfxResponse);

    // Validate Database update
    var captor = updateEvent(updateEvent);
    verify(procurementEventRepo).save(captor.capture());
    assertEquals(UPDATED_EVENT_NAME, captor.getValue().getEventName());
    assertEquals(UPDATED_EVENT_TYPE, captor.getValue().getEventType());
    assertEquals(PRINCIPAL, captor.getValue().getUpdatedBy());

  }

  @Test
  void testUpdateProcurementEventName() throws Exception {

    // Stub some objects
    var updateEvent = new UpdateEvent();
    updateEvent.setName(UPDATED_EVENT_NAME);

    var rfxSetting = RfxSetting.builder().shortDescription(UPDATED_EVENT_NAME).build();
    var rfx = Rfx.builder().rfxSetting(rfxSetting).build();
    var updateRfx = new CreateUpdateRfx(OperationCode.UPDATE, rfx);

    // Validate Jaggaer update
    var createUpdateRfxResponse = new CreateUpdateRfxResponse();
    createUpdateRfxResponse.setReturnCode(0);
    createUpdateRfxResponse.setReturnMessage("OK");

    when(userProfileService.resolveBuyerUserByEmail(PRINCIPAL)).thenReturn(JAGGAER_USER);
    when(jaggaerWebClient.post().uri(jaggaerAPIConfig.getCreateRfx().get("endpoint"))
        .bodyValue(argThat(new UpdateEventNameMatcher(updateRfx))).retrieve()
        .bodyToMono(eq(CreateUpdateRfxResponse.class))
        .block(Duration.ofSeconds(jaggaerAPIConfig.getTimeoutDuration())))
            .thenReturn(createUpdateRfxResponse);

    // Invoke
    var captor = updateEvent(updateEvent);

    verify(procurementEventRepo).save(captor.capture());
    assertEquals(UPDATED_EVENT_NAME, captor.getValue().getEventName());
    assertEquals(PRINCIPAL, captor.getValue().getUpdatedBy());

  }

  @Test
  void testUpdateProcurementEventType() throws Exception {

    // Stub some objects
    var updateEvent = new UpdateEvent();
    updateEvent.setEventType(EVENT_TYPE);

    // Invoke
    var captor = updateEvent(updateEvent);

    verify(procurementEventRepo).save(captor.capture());
    assertEquals(UPDATED_EVENT_TYPE, captor.getValue().getEventType());
    assertEquals(PRINCIPAL, captor.getValue().getUpdatedBy());
  }

  @Test
  void testUpdateProcurementEventThrowsIllegalArgumentException2() throws Exception {

    var updateEvent = new UpdateEvent();

    // Mock behaviours
    when(userProfileService.resolveBuyerUserByEmail(PRINCIPAL)).thenReturn(JAGGAER_USER);

    // Invoke & assert
    var ex = assertThrows(IllegalArgumentException.class, () -> procurementEventService
        .updateProcurementEvent(PROC_PROJECT_ID, "EVENT-1", updateEvent, PRINCIPAL));
    assertEquals("Event ID 'EVENT-1' is not in the expected format", ex.getMessage());
  }

  @Test
  void testUpdateProcurementEventThrowsResourceNotFoundApplicationException() throws Exception {

    var updateEvent = new UpdateEvent();

    // Mock behaviours
    when(userProfileService.resolveBuyerUserByEmail(PRINCIPAL)).thenReturn(JAGGAER_USER);

    // Invoke & assert
    var ex = assertThrows(ResourceNotFoundException.class, () -> procurementEventService
        .updateProcurementEvent(PROC_PROJECT_ID, PROC_EVENT_ID, updateEvent, PRINCIPAL));
    assertEquals("Event 'ocds-b5fd17-2' not found", ex.getMessage());
  }

  @Test
  void testUpdateProcurementEventThrowsJaggaerApplicationException() throws Exception {
    // Stub some objects
    var updateEvent = new UpdateEvent();
    updateEvent.setName(UPDATED_EVENT_NAME);

    var jaggaerErrorResponse = new CreateUpdateRfxResponse();
    jaggaerErrorResponse.setReturnCode(1);
    jaggaerErrorResponse.setReturnMessage("NOT OK");
    var procurementProject = new ProcurementProject();
    var procurementEvent = ProcurementEvent.builder().build();

    // Mock behaviours
    when(userProfileService.resolveBuyerUserByEmail(PRINCIPAL)).thenReturn(JAGGAER_USER);
    when(jaggaerWebClient.post().uri(jaggaerAPIConfig.getCreateRfx().get("endpoint"))
        .bodyValue(any(CreateUpdateRfx.class)).retrieve()
        .bodyToMono(eq(CreateUpdateRfxResponse.class))
        .block(Duration.ofSeconds(jaggaerAPIConfig.getTimeoutDuration())))
            .thenReturn(jaggaerErrorResponse);

    when(procurementProjectRepo.findById(PROC_PROJECT_ID)).then(mock -> {
      procurementProject.setId(PROC_PROJECT_ID);
      procurementProject.setProjectName(PROJECT_NAME);
      return Optional.of(procurementProject);
    });

    when(procurementEventRepo.findProcurementEventByIdAndOcdsAuthorityNameAndOcidPrefix(
        PROC_EVENT_DB_ID, OCDS_AUTH_NAME, OCID_PREFIX)).then(mock -> {
          procurementProject.setId(PROC_PROJECT_ID);
          procurementEvent.setProject(procurementProject);

          return Optional.of(procurementEvent);
        });

    // Invoke & assert
    var jagEx = assertThrows(JaggaerApplicationException.class, () -> procurementEventService
        .updateProcurementEvent(PROC_PROJECT_ID, PROC_EVENT_ID, updateEvent, PRINCIPAL));
    assertEquals("Jaggaer application exception, Code: [1], Message: [NOT OK]", jagEx.getMessage());
  }

  /**
   * Custom matcher to verify the object sent to Jaggaer to update an event.
   *
   */
  private class UpdateEventNameMatcher implements ArgumentMatcher<CreateUpdateRfx> {

    private final CreateUpdateRfx left;

    UpdateEventNameMatcher(final CreateUpdateRfx left) {
      this.left = left;
    }

    @Override
    public boolean matches(final CreateUpdateRfx right) {
      return left.getRfx().getRfxSetting().getShortDescription()
          .equals(right.getRfx().getRfxSetting().getShortDescription())
          && right.getOperationCode() == OperationCode.UPDATE;
    }
  }

  private ArgumentCaptor<ProcurementEvent> updateEvent(final UpdateEvent updateEvent)
      throws Exception {

    // Stub some objects
    var procurementProject = new ProcurementProject();
    var procurementEvent = ProcurementEvent.builder().build();

    when(procurementEventRepo.findProcurementEventByIdAndOcdsAuthorityNameAndOcidPrefix(
        PROC_EVENT_DB_ID, OCDS_AUTH_NAME, OCID_PREFIX)).then(mock -> {
          procurementProject.setId(PROC_PROJECT_ID);
          procurementEvent.setProject(procurementProject);
          procurementEvent.setId(PROC_EVENT_DB_ID);
          procurementEvent.setExternalEventId(RFX_ID);
          procurementEvent.setExternalReferenceId(RFX_REF_CODE);
          return Optional.of(procurementEvent);
        });

    // Invoke
    ArgumentCaptor<ProcurementEvent> captor = ArgumentCaptor.forClass(ProcurementEvent.class);

    procurementEventService.updateProcurementEvent(PROC_PROJECT_ID, PROC_EVENT_ID, updateEvent,
        PRINCIPAL);

    // Verify entity was retrieved as expected
    verify(procurementEventRepo, times(1))
        .findProcurementEventByIdAndOcdsAuthorityNameAndOcidPrefix(PROC_EVENT_DB_ID, OCDS_AUTH_NAME,
            OCID_PREFIX);

    return captor;

  }

}
