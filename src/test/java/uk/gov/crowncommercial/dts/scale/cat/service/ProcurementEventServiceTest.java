package uk.gov.crowncommercial.dts.scale.cat.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import javax.validation.ValidationException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.web.reactive.function.client.WebClient;
import uk.gov.crowncommercial.dts.scale.cat.config.ApplicationFlagsConfig;
import uk.gov.crowncommercial.dts.scale.cat.config.DocumentConfig;
import uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.config.OcdsConfig;
import uk.gov.crowncommercial.dts.scale.cat.exception.JaggaerApplicationException;
import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.Assessment;
import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.DimensionRequirement;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.*;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent.ProcurementEventBuilder;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.*;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.*;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.SubUsers.SubUser;
import uk.gov.crowncommercial.dts.scale.cat.repo.*;
import uk.gov.crowncommercial.dts.scale.cat.repo.readonly.CalculationBaseRepo;
import uk.gov.crowncommercial.dts.scale.cat.service.ca.AssessmentService;
import uk.gov.crowncommercial.dts.scale.cat.service.documentupload.DocumentUploadService;
import uk.gov.crowncommercial.dts.scale.cat.utils.TendersAPIModelUtils;

/**
 * Service layer tests
 */
@SpringBootTest(classes = {ProcurementEventService.class, JaggaerAPIConfig.class, OcdsConfig.class,
    DocumentConfig.class, TendersAPIModelUtils.class, RetryableTendersDBDelegate.class,
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
  private static final String ORIGINAL_EVENT_NAME = "Old Name";
  private static final String UPDATED_EVENT_NAME = "New Name";
  private static final String ORIGINAL_EVENT_TYPE = "TBD";
  private static final String UPDATED_EVENT_TYPE = "RFI";
  private static final String UPDATED_EVENT_TYPE_CAP_ASS = "FCA";
  private static final String BUYER_COMPANY_BRAVO_ID = "54321";
  private static final Boolean DOWNSELECTED_SUPPLIERS = true;
  private static final Optional<SubUser> JAGGAER_USER =
      Optional.of(SubUser.builder().userId(JAGGAER_USER_ID).email(PRINCIPAL).build());
  private static final CompanyInfo BUYER_COMPANY_INFO =
      CompanyInfo.builder().bravoId(BUYER_COMPANY_BRAVO_ID).build();
  private static final String SUPPLIER_ID = "US-DUNS-227015716";
  private static final Integer JAGGAER_SUPPLIER_ID = 21399;
  private static final String DESCRIPTION = "Description";
  private static final String CRITERION_TITLE = "Criteria 1";
  private static final Integer ASSESSMENT_ID = 1;
  private static final Integer WEIGHTING = 10;
  private static final Integer ASSESSMENT_SUPPLIER_TARGET = 10;

  @MockBean(answer = Answers.RETURNS_DEEP_STUBS)
  private WebClient jaggaerWebClient;

  @MockBean
  private UserProfileService userProfileService;

  @MockBean
  private ProcurementProjectRepo procurementProjectRepo;

  @MockBean
  private ProcurementEventRepo procurementEventRepo;

  @MockBean
  private SupplierService supplierService;

  @MockBean
  private OrganisationMappingRepo organisationMappingRepo;

  @Autowired
  private ProcurementEventService procurementEventService;

  @Autowired
  private JaggaerAPIConfig jaggaerAPIConfig;

  @MockBean
  private JaggaerService jaggaerService;

  @MockBean
  private ValidationService validationService;

  @MockBean
  private DocumentTemplateRepo documentTemplateRepo;

  @MockBean
  private CriteriaService criteriaService;

  @MockBean
  private AssessmentService assessmentService;

  @MockBean
  private ConclaveService conclaveService;

  @MockBean
  private JourneyRepo journeyRepo;

  @MockBean
  private AssessmentRepo assessmentRepo;

  @MockBean
  private AssessmentToolRepo assessmentToolRepo;

  @MockBean
  private AssessmentDimensionWeightingRepo assessmentDimensionWeightingRepo;

  @MockBean
  private DimensionRepo dimensionRepo;

  @MockBean
  private AssessmentSelectionRepo assessmentSelectionRepo;

  @MockBean
  private RequirementTaxonRepo requirementTaxonRepo;

  @MockBean
  private AssessmentTaxonRepo assessmentTaxonRepo;

  @MockBean
  private CalculationBaseRepo calculationBaseRepo;

  @MockBean
  private AssessmentResultRepo assessmentResultRepo;

  @MockBean
  private ProjectUserMappingRepo projectUserMappingRepo;

  @MockBean
  private SupplierSelectionRepo supplierSelectionRepo;

  @MockBean
  private DocumentUploadService documentUploadService;

  @MockBean
  private DocumentTemplateService documentTemplateService;

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

    var procurementProject = ProcurementProject.builder()
        .caNumber(agreementDetails.getAgreementId()).lotNumber(agreementDetails.getLotId()).build();
    var procurementEvent = ProcurementEvent.builder().build();

    var rfxSetting = RfxSetting.builder().shortDescription(UPDATED_EVENT_NAME).statusCode(100)
        .publishDate(OffsetDateTime.now()).closeDate(OffsetDateTime.now()).build();
    var rfxResponse = new ExportRfxResponse();
    rfxResponse.setRfxSetting(rfxSetting);

    // Mock behaviours
    when(userProfileService.resolveBuyerUserByEmail(PRINCIPAL)).thenReturn(JAGGAER_USER);
    when(userProfileService.resolveBuyerCompanyByEmail(PRINCIPAL)).thenReturn(BUYER_COMPANY_INFO);
    when(jaggaerService.createUpdateRfx(any(Rfx.class), any(OperationCode.class)))
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

    when(jaggaerService.getRfx(anyString())).thenReturn(rfxResponse);

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

    verify(assessmentService, never()).createEmptyAssessment(any(), any(), any(), any());
  }

  @Test
  void testCreateFromProjectCreateAssessment() throws Exception {

    // Stub some objects
    var agreementDetails = new AgreementDetails();
    agreementDetails.agreementId(CA_NUMBER);
    agreementDetails.setLotId(LOT_NUMBER);

    var procurementProject = ProcurementProject.builder()
        .caNumber(agreementDetails.getAgreementId()).lotNumber(agreementDetails.getLotId()).build();
    var procurementEvent = ProcurementEvent.builder().build();

    var rfxSetting = RfxSetting.builder().shortDescription(UPDATED_EVENT_NAME).statusCode(100)
        .publishDate(OffsetDateTime.now()).closeDate(OffsetDateTime.now()).build();
    var rfxResponse = new ExportRfxResponse();
    rfxResponse.setRfxSetting(rfxSetting);

    // Mock behaviours
    when(userProfileService.resolveBuyerUserByEmail(PRINCIPAL)).thenReturn(JAGGAER_USER);
    when(userProfileService.resolveBuyerCompanyByEmail(PRINCIPAL)).thenReturn(BUYER_COMPANY_INFO);

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

    when(assessmentService.createEmptyAssessment(CA_NUMBER, LOT_NUMBER, DefineEventType.FCA,
        PRINCIPAL)).thenReturn(ASSESSMENT_ID);

    when(jaggaerService.getRfx(any())).thenReturn(rfxResponse);

    var createEventNonOCDS = new CreateEventNonOCDS();
    createEventNonOCDS.setEventType(DefineEventType.FCA);
    createEvent.setNonOCDS(createEventNonOCDS);

    // Invoke
    ArgumentCaptor<ProcurementEvent> captor = ArgumentCaptor.forClass(ProcurementEvent.class);
    var eventStatus = procurementEventService.createEvent(PROC_PROJECT_ID, createEvent,
        DOWNSELECTED_SUPPLIERS, PRINCIPAL);

    // Verify that entity was created as expected
    verify(procurementEventRepo, times(1)).save(captor.capture());
    var capturedProcEvent = captor.getValue();
    assertEquals(CA_NUMBER + '-' + LOT_NUMBER + "-CCS-FCA", capturedProcEvent.getEventName());
    assertEquals(OCID_PREFIX, capturedProcEvent.getOcidPrefix());
    assertEquals(OCDS_AUTH_NAME, capturedProcEvent.getOcdsAuthorityName());
    assertEquals(PROC_PROJECT_ID, capturedProcEvent.getProject().getId());
    assertEquals(PRINCIPAL, capturedProcEvent.getCreatedBy());
    assertEquals(ViewEventType.FCA.getValue(), capturedProcEvent.getEventType());
    assertEquals(DOWNSELECTED_SUPPLIERS, capturedProcEvent.getDownSelectedSuppliers());
    assertNotNull(capturedProcEvent.getCreatedAt());
    assertNotNull(capturedProcEvent.getUpdatedAt());

    // Verify that event ID is generated correctly by entity
    assertEquals(OCDS_AUTH_NAME + "-" + OCID_PREFIX + "-1", procurementEvent.getEventID());

    // Verify that response is correct
    assertEquals(CA_NUMBER + '-' + LOT_NUMBER + "-CCS-FCA", eventStatus.getTitle());
    assertEquals(ReleaseTag.TENDER, eventStatus.getEventStage());
    assertEquals(TenderStatus.PLANNING, eventStatus.getStatus());
    assertEquals(ViewEventType.FCA, eventStatus.getEventType());
    assertEquals(ASSESSMENT_ID, eventStatus.getAssessmentId());

    verify(assessmentService).createEmptyAssessment(CA_NUMBER, LOT_NUMBER, DefineEventType.FCA,
        PRINCIPAL);
    verify(jaggaerWebClient, never()).post();
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

    var procurementProject = ProcurementProject.builder()
        .caNumber(agreementDetails.getAgreementId()).lotNumber(agreementDetails.getLotId()).build();
    var procurementEvent = ProcurementEvent.builder().build();

    var rfxSetting = RfxSetting.builder().shortDescription(UPDATED_EVENT_NAME).statusCode(100)
        .publishDate(OffsetDateTime.now()).closeDate(OffsetDateTime.now()).build();
    var rfxResponse = new ExportRfxResponse();
    rfxResponse.setRfxSetting(rfxSetting);

    // Mock behaviours
    when(userProfileService.resolveBuyerUserByEmail(PRINCIPAL)).thenReturn(JAGGAER_USER);
    when(userProfileService.resolveBuyerCompanyByEmail(PRINCIPAL)).thenReturn(BUYER_COMPANY_INFO);
    when(jaggaerService.createUpdateRfx(any(Rfx.class), any(OperationCode.class)))
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

    when(jaggaerService.getRfx(anyString())).thenReturn(rfxResponse);

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

    verify(assessmentService, never()).createEmptyAssessment(any(), any(), any(), any());
  }

  @Test
  void testUpdateProcurementEventNameAndType() throws Exception {

    // Create test update objects
    var updateEvent = new UpdateEvent();
    updateEvent.setName(UPDATED_EVENT_NAME);
    updateEvent.setEventType(DefineEventType.fromValue(UPDATED_EVENT_TYPE));

    var rfxSetting = RfxSetting.builder().shortDescription(UPDATED_EVENT_NAME).statusCode(100)
        .publishDate(OffsetDateTime.now()).closeDate(OffsetDateTime.now()).build();
    var rfxResponse = new ExportRfxResponse();
    rfxResponse.setRfxSetting(rfxSetting);

    var event = new ProcurementEvent();
    event.setId(PROC_EVENT_DB_ID);
    event.setExternalEventId(PROC_EVENT_ID);
    event.setEventType(ORIGINAL_EVENT_TYPE);

    when(userProfileService.resolveBuyerUserByEmail(PRINCIPAL)).thenReturn(JAGGAER_USER);
    when(validationService.validateProjectAndEventIds(PROC_PROJECT_ID, PROC_EVENT_ID))
        .thenReturn(event);
    when(jaggaerService.getRfx(PROC_EVENT_ID)).thenReturn(rfxResponse);

    // Invoke
    ArgumentCaptor<ProcurementEvent> captor = ArgumentCaptor.forClass(ProcurementEvent.class);
    ArgumentCaptor<Rfx> rfxCaptor = ArgumentCaptor.forClass(Rfx.class);

    procurementEventService.updateProcurementEvent(PROC_PROJECT_ID, PROC_EVENT_ID, updateEvent,
        PRINCIPAL);

    // Verify
    verify(procurementEventRepo).save(captor.capture());
    assertEquals(UPDATED_EVENT_NAME, captor.getValue().getEventName());
    assertEquals(UPDATED_EVENT_TYPE, captor.getValue().getEventType());
    assertEquals(PRINCIPAL, captor.getValue().getUpdatedBy());

    verify(jaggaerService).createUpdateRfx(rfxCaptor.capture(), eq(OperationCode.CREATEUPDATE));
    assertEquals(UPDATED_EVENT_NAME, rfxCaptor.getValue().getRfxSetting().getShortDescription());
  }

  @Test
  void testUpdateProcurementEventName() throws Exception {

    // Create test update objects
    var updateEvent = new UpdateEvent();
    updateEvent.setName(UPDATED_EVENT_NAME);

    var rfxSetting = RfxSetting.builder().shortDescription(UPDATED_EVENT_NAME).statusCode(100)
        .publishDate(OffsetDateTime.now()).closeDate(OffsetDateTime.now()).build();
    var rfxResponse = new ExportRfxResponse();
    rfxResponse.setRfxSetting(rfxSetting);

    var event = new ProcurementEvent();
    event.setId(PROC_EVENT_DB_ID);
    event.setExternalEventId(PROC_EVENT_ID);
    event.setEventType(ORIGINAL_EVENT_TYPE);

    when(userProfileService.resolveBuyerUserByEmail(PRINCIPAL)).thenReturn(JAGGAER_USER);
    when(validationService.validateProjectAndEventIds(PROC_PROJECT_ID, PROC_EVENT_ID))
        .thenReturn(event);
    when(jaggaerService.getRfx(PROC_EVENT_ID)).thenReturn(rfxResponse);

    // Invoke
    ArgumentCaptor<ProcurementEvent> captor = ArgumentCaptor.forClass(ProcurementEvent.class);
    ArgumentCaptor<Rfx> rfxCaptor = ArgumentCaptor.forClass(Rfx.class);

    procurementEventService.updateProcurementEvent(PROC_PROJECT_ID, PROC_EVENT_ID, updateEvent,
        PRINCIPAL);

    // Assert
    verify(procurementEventRepo).save(captor.capture());
    assertEquals(UPDATED_EVENT_NAME, captor.getValue().getEventName());
    assertEquals(PRINCIPAL, captor.getValue().getUpdatedBy());

    verify(jaggaerService).createUpdateRfx(rfxCaptor.capture(), eq(OperationCode.CREATEUPDATE));
    assertEquals(UPDATED_EVENT_NAME, rfxCaptor.getValue().getRfxSetting().getShortDescription());
  }

  @Test
  void testUpdateProcurementEventType() throws Exception {

    // Create test update objects
    var updateEvent = new UpdateEvent();
    updateEvent.setEventType(DefineEventType.fromValue(UPDATED_EVENT_TYPE));

    var rfxSetting = RfxSetting.builder().shortDescription(ORIGINAL_EVENT_NAME).statusCode(100)
        .publishDate(OffsetDateTime.now()).closeDate(OffsetDateTime.now()).build();
    var rfxResponse = new ExportRfxResponse();
    rfxResponse.setRfxSetting(rfxSetting);

    var event = new ProcurementEvent();
    event.setId(PROC_EVENT_DB_ID);
    event.setExternalEventId(PROC_EVENT_ID);
    event.setEventType(ORIGINAL_EVENT_TYPE);

    when(userProfileService.resolveBuyerUserByEmail(PRINCIPAL)).thenReturn(JAGGAER_USER);
    when(validationService.validateProjectAndEventIds(PROC_PROJECT_ID, PROC_EVENT_ID))
        .thenReturn(event);
    when(jaggaerService.getRfx(PROC_EVENT_ID)).thenReturn(rfxResponse);

    // Invoke
    ArgumentCaptor<ProcurementEvent> captor = ArgumentCaptor.forClass(ProcurementEvent.class);

    procurementEventService.updateProcurementEvent(PROC_PROJECT_ID, PROC_EVENT_ID, updateEvent,
        PRINCIPAL);

    // Verify
    verify(procurementEventRepo).save(captor.capture());
    assertEquals(UPDATED_EVENT_TYPE, captor.getValue().getEventType());
    assertEquals(PRINCIPAL, captor.getValue().getUpdatedBy());

    verify(jaggaerService, times(0)).createUpdateRfx(any(), eq(OperationCode.CREATEUPDATE));
  }

  @Test
  void testUpdateProcurementEventTypeCreateAssessment() throws Exception {

    // Create test update objects
    var updateEvent =
        new UpdateEvent().eventType(DefineEventType.fromValue(UPDATED_EVENT_TYPE_CAP_ASS));

    var rfxSetting = RfxSetting.builder().shortDescription(ORIGINAL_EVENT_NAME).statusCode(100)
        .publishDate(OffsetDateTime.now()).closeDate(OffsetDateTime.now()).build();
    var rfxResponse = new ExportRfxResponse();
    rfxResponse.setRfxSetting(rfxSetting);

    var project = new ProcurementProject();
    project.setCaNumber(CA_NUMBER);
    project.setLotNumber(LOT_NUMBER);

    var event = new ProcurementEvent();
    event.setId(PROC_EVENT_DB_ID);
    event.setExternalEventId(PROC_EVENT_ID);
    event.setEventType(ORIGINAL_EVENT_TYPE);
    event.setProject(project);

    when(userProfileService.resolveBuyerUserByEmail(PRINCIPAL)).thenReturn(JAGGAER_USER);
    when(validationService.validateProjectAndEventIds(PROC_PROJECT_ID, PROC_EVENT_ID))
        .thenReturn(event);
    when(assessmentService.createEmptyAssessment(CA_NUMBER, LOT_NUMBER,
        DefineEventType.fromValue(UPDATED_EVENT_TYPE_CAP_ASS), PRINCIPAL))
            .thenReturn(ASSESSMENT_ID);

    when(jaggaerService.getRfx(PROC_EVENT_ID)).thenReturn(rfxResponse);

    // Invoke
    var captor = ArgumentCaptor.forClass(ProcurementEvent.class);

    procurementEventService.updateProcurementEvent(PROC_PROJECT_ID, PROC_EVENT_ID, updateEvent,
        PRINCIPAL);

    // Verify
    verify(validationService).validateUpdateEventAssessment(updateEvent, event, PRINCIPAL);
    verify(assessmentService).createEmptyAssessment(CA_NUMBER, LOT_NUMBER,
        DefineEventType.fromValue(UPDATED_EVENT_TYPE_CAP_ASS), PRINCIPAL);
    verify(procurementEventRepo).save(captor.capture());
    assertEquals(UPDATED_EVENT_TYPE_CAP_ASS, captor.getValue().getEventType());
    assertEquals(PRINCIPAL, captor.getValue().getUpdatedBy());
    assertEquals(ASSESSMENT_ID, captor.getValue().getAssessmentId());
    verify(jaggaerService, times(0)).createUpdateRfx(any(), eq(OperationCode.CREATEUPDATE));
  }

  @Test
  void testUpdateProcurementEventTypeUpdateAssessment() throws Exception {

    // Create test update objects
    var updateEvent =
        new UpdateEvent().eventType(DefineEventType.fromValue(UPDATED_EVENT_TYPE_CAP_ASS))
            .assessmentId(ASSESSMENT_ID).assessmentSupplierTarget(10);

    var rfxSetting = RfxSetting.builder().shortDescription(ORIGINAL_EVENT_NAME).statusCode(100)
        .publishDate(OffsetDateTime.now()).closeDate(OffsetDateTime.now()).build();
    var rfxResponse = new ExportRfxResponse();
    rfxResponse.setRfxSetting(rfxSetting);

    var project = new ProcurementProject();
    project.setCaNumber(CA_NUMBER);
    project.setLotNumber(LOT_NUMBER);

    var event = new ProcurementEvent();
    event.setId(PROC_EVENT_DB_ID);
    event.setExternalEventId(PROC_EVENT_ID);
    event.setEventType(ORIGINAL_EVENT_TYPE);
    event.setProject(project);

    when(userProfileService.resolveBuyerUserByEmail(PRINCIPAL)).thenReturn(JAGGAER_USER);
    when(validationService.validateProjectAndEventIds(PROC_PROJECT_ID, PROC_EVENT_ID))
        .thenReturn(event);

    when(jaggaerService.getRfx(PROC_EVENT_ID)).thenReturn(rfxResponse);

    // Invoke
    var captor = ArgumentCaptor.forClass(ProcurementEvent.class);

    procurementEventService.updateProcurementEvent(PROC_PROJECT_ID, PROC_EVENT_ID, updateEvent,
        PRINCIPAL);

    // Verify
    verify(validationService).validateUpdateEventAssessment(updateEvent, event, PRINCIPAL);
    verify(assessmentService, never()).createEmptyAssessment(CA_NUMBER, LOT_NUMBER,
        DefineEventType.fromValue(UPDATED_EVENT_TYPE_CAP_ASS), PRINCIPAL);
    verify(procurementEventRepo).save(captor.capture());
    assertEquals(UPDATED_EVENT_TYPE_CAP_ASS, captor.getValue().getEventType());
    assertEquals(PRINCIPAL, captor.getValue().getUpdatedBy());
    assertEquals(10, captor.getValue().getAssessmentSupplierTarget());
    verify(jaggaerService, times(0)).createUpdateRfx(any(), eq(OperationCode.CREATEUPDATE));
  }

  @Test
  void testUpdateProcurementEventThrowsJaggaerApplicationException() throws Exception {
    // Stub some objects
    var updateEvent = new UpdateEvent();
    updateEvent.setName(UPDATED_EVENT_NAME);

    var event = new ProcurementEvent();
    event.setId(PROC_EVENT_DB_ID);
    event.setExternalEventId(PROC_EVENT_ID);
    event.setEventType(ORIGINAL_EVENT_TYPE);

    // Mock behaviours
    when(userProfileService.resolveBuyerUserByEmail(PRINCIPAL)).thenReturn(JAGGAER_USER);
    when(validationService.validateProjectAndEventIds(PROC_PROJECT_ID, PROC_EVENT_ID))
        .thenReturn(event);

    when(jaggaerService.createUpdateRfx(any(), eq(OperationCode.CREATEUPDATE)))
        .thenThrow(new JaggaerApplicationException(1, "NOT OK"));

    // Invoke & assert
    var jagEx = assertThrows(JaggaerApplicationException.class, () -> procurementEventService
        .updateProcurementEvent(PROC_PROJECT_ID, PROC_EVENT_ID, updateEvent, PRINCIPAL));
    assertEquals("Jaggaer application exception, Code: [1], Message: [NOT OK]", jagEx.getMessage());
  }

  @Test
  void testUpdateProcurementEventTypeThrowsIllegalArgumentException() throws Exception {
    // Stub some objects
    var updateEvent = new UpdateEvent();
    updateEvent.setEventType(DefineEventType.fromValue(UPDATED_EVENT_TYPE));

    var event = new ProcurementEvent();
    event.setEventType("RFI"); // Not 'TBD', so rules state cannot update

    // Mock behaviours
    when(userProfileService.resolveBuyerUserByEmail(PRINCIPAL)).thenReturn(JAGGAER_USER);
    when(validationService.validateProjectAndEventIds(PROC_PROJECT_ID, PROC_EVENT_ID))
        .thenReturn(event);

    when(jaggaerService.createUpdateRfx(any(), eq(OperationCode.CREATEUPDATE)))
        .thenThrow(new JaggaerApplicationException(1, "NOT OK"));

    // Invoke & assert
    var ex = assertThrows(IllegalArgumentException.class, () -> procurementEventService
        .updateProcurementEvent(PROC_PROJECT_ID, PROC_EVENT_ID, updateEvent, PRINCIPAL));
    assertEquals("Cannot update an existing event type of 'RFI'", ex.getMessage());
  }

  @Test
  void testGetSuppliersFromJaggaer() throws Exception {

    var event = new ProcurementEvent();
    event.setExternalEventId(RFX_ID);
    event.setEventType(UPDATED_EVENT_TYPE);

    var companyData = CompanyData.builder().id(JAGGAER_SUPPLIER_ID).build();
    var supplier = Supplier.builder().companyData(companyData).build();
    var suppliersList = SuppliersList.builder().supplier(Arrays.asList(supplier)).build();
    var rfxResponse = new ExportRfxResponse();
    rfxResponse.setSuppliersList(suppliersList);

    var orgMapping = new OrganisationMapping();
    orgMapping.setExternalOrganisationId(JAGGAER_SUPPLIER_ID);
    orgMapping.setOrganisationId(SUPPLIER_ID);

    // Mock behaviours
    when(validationService.validateProjectAndEventIds(PROC_PROJECT_ID, PROC_EVENT_ID))
        .thenReturn(event);
    when(jaggaerService.getRfx(RFX_ID)).thenReturn(rfxResponse);
    when(organisationMappingRepo.findByExternalOrganisationId(JAGGAER_SUPPLIER_ID))
        .thenReturn(Optional.of(orgMapping));

    var response = procurementEventService.getSuppliers(PROC_PROJECT_ID, PROC_EVENT_ID);

    // Verify
    assertEquals(1, response.size());
    assertEquals(SUPPLIER_ID, response.stream().findFirst().get().getId());

  }

  @Test
  void testAddSuppliersToJaggaer() throws Exception {

    var org = new OrganizationReference();
    org.setId(SUPPLIER_ID);

    var event = new ProcurementEvent();
    event.setExternalEventId(RFX_ID);
    event.setEventType(UPDATED_EVENT_TYPE);
    event.setAssessmentId(ASSESSMENT_ID);

    var mapping = new OrganisationMapping();
    mapping.setExternalOrganisationId(JAGGAER_SUPPLIER_ID);
    mapping.setOrganisationId(SUPPLIER_ID);

    // Mock behaviours
    when(validationService.validateProjectAndEventIds(PROC_PROJECT_ID, PROC_EVENT_ID))
        .thenReturn(event);
    when(organisationMappingRepo.findByOrganisationIdIn(Set.of(SUPPLIER_ID)))
        .thenReturn(Set.of(mapping));

    ArgumentCaptor<Rfx> rfxCaptor = ArgumentCaptor.forClass(Rfx.class);

    var response = procurementEventService.addSuppliers(PROC_PROJECT_ID, PROC_EVENT_ID,
        List.of(org), false, PRINCIPAL);

    // Verify
    verify(jaggaerService).createUpdateRfx(rfxCaptor.capture(), eq(OperationCode.CREATEUPDATE));
    assertEquals(1, rfxCaptor.getValue().getSuppliersList().getSupplier().size());
    assertEquals(JAGGAER_SUPPLIER_ID,
        rfxCaptor.getValue().getSuppliersList().getSupplier().get(0).getCompanyData().getId());

    assertEquals(SUPPLIER_ID, response.stream().findFirst().get().getId());
  }

  @Test
  void testAddSuppliersToTenderDbThenThrowValidationException() throws Exception {

    var org = new OrganizationReference();
    org.setId(SUPPLIER_ID);

    var event = new ProcurementEvent();
    event.setExternalEventId(RFX_ID);
    // event = FCA
    event.setEventType(UPDATED_EVENT_TYPE_CAP_ASS);
    event.setAssessmentId(ASSESSMENT_ID);

    var mapping = new OrganisationMapping();
    mapping.setExternalOrganisationId(JAGGAER_SUPPLIER_ID);
    mapping.setOrganisationId(SUPPLIER_ID);

    var assessment = new Assessment();
    assessment.addDimensionRequirementsItem(new DimensionRequirement().weighting(WEIGHTING));

    // Mock behaviours
    when(validationService.validateProjectAndEventIds(PROC_PROJECT_ID, PROC_EVENT_ID))
        .thenReturn(event);
    when(organisationMappingRepo.findByOrganisationIdIn(Set.of(SUPPLIER_ID)))
        .thenReturn(Set.of(mapping));
    when(assessmentService.getAssessment(ASSESSMENT_ID, Optional.empty())).thenReturn(assessment);

    // Invoke
    var thrown = Assertions.assertThrows(ValidationException.class, () -> procurementEventService
        .addSuppliers(PROC_PROJECT_ID, PROC_EVENT_ID, List.of(org), false, PRINCIPAL));

    // Assert
    Assertions.assertEquals(
        "All dimensions must have 100% weightings prior to the supplier(s) can be added to the event",
        thrown.getMessage());
  }

  @Test
  void testDeleteSupplierFromJaggaer() throws Exception {

    var event = new ProcurementEvent();
    event.setExternalEventId(RFX_ID);
    event.setEventType(UPDATED_EVENT_TYPE);

    var mapping = new OrganisationMapping();
    mapping.setExternalOrganisationId(JAGGAER_SUPPLIER_ID);
    mapping.setOrganisationId(SUPPLIER_ID);

    var companyData = CompanyData.builder().id(JAGGAER_SUPPLIER_ID).build();
    var supplier = Supplier.builder().companyData(companyData).build();
    var suppliersList = SuppliersList.builder().supplier(Arrays.asList(supplier)).build();
    var rfxResponse = new ExportRfxResponse();
    rfxResponse.setSuppliersList(suppliersList);

    // Mock behaviours
    when(validationService.validateProjectAndEventIds(PROC_PROJECT_ID, PROC_EVENT_ID))
        .thenReturn(event);
    when(organisationMappingRepo.findByOrganisationId(SUPPLIER_ID))
        .thenReturn(Optional.of(mapping));
    when(jaggaerService.getRfx(RFX_ID)).thenReturn(rfxResponse);

    ArgumentCaptor<Rfx> rfxCaptor = ArgumentCaptor.forClass(Rfx.class);

    procurementEventService.deleteSupplier(PROC_PROJECT_ID, PROC_EVENT_ID, SUPPLIER_ID, PRINCIPAL);

    // Verify (supplier removed from Rfx for reset update)
    verify(jaggaerService).createUpdateRfx(rfxCaptor.capture(), eq(OperationCode.UPDATE_RESET));
    assertEquals(0, rfxCaptor.getValue().getSuppliersList().getSupplier().size());

  }

  @Test
  void testGetEventNonAssessmentType() throws Exception {

    var procurementEventBuilder = ProcurementEvent.builder();
    var criterion = new EvalCriteria();
    criterion.setTitle(CRITERION_TITLE);
    var buyerQuestions = new HashSet<EvalCriteria>();
    buyerQuestions.add(criterion);

    when(criteriaService.getEvalCriteria(PROC_PROJECT_ID, PROC_EVENT_ID, true))
        .thenReturn(buyerQuestions);

    var eventDetail = testGetEventHelper(ViewEventType.RFI, procurementEventBuilder);

    // Additional assertions / verifications
    assertEquals(CRITERION_TITLE,
        eventDetail.getNonOCDS().getBuyerQuestions().stream().findFirst().get().getTitle());
    verify(criteriaService).getEvalCriteria(PROC_PROJECT_ID, PROC_EVENT_ID, true);
  }

  @Test
  void testGetEventAssessmentType() throws Exception {
    var procurementEventBuilder = ProcurementEvent.builder().assessmentId(ASSESSMENT_ID)
        .assessmentSupplierTarget(ASSESSMENT_SUPPLIER_TARGET);
    var eventDetail = testGetEventHelper(ViewEventType.FCA, procurementEventBuilder);

    assertEquals(ASSESSMENT_ID, eventDetail.getNonOCDS().getAssessmentId());
    assertEquals(ASSESSMENT_SUPPLIER_TARGET,
        eventDetail.getNonOCDS().getAssessmentSupplierTarget());

    verify(criteriaService, never()).getEvalCriteria(anyInt(), anyString(), anyBoolean());
  }

  EventDetail testGetEventHelper(final ViewEventType eventType,
      final ProcurementEventBuilder procurementEventBuilder) {
    var procurementProject =
        ProcurementProject.builder().caNumber(CA_NUMBER).lotNumber(LOT_NUMBER).build();
    var procurementEvent =
        procurementEventBuilder.project(procurementProject).eventType(eventType.name())
            .externalEventId(RFX_ID).externalReferenceId(RFX_REF_CODE).build();

    var rfxSetting = RfxSetting.builder().statusCode(100).rfxId(RFX_ID)
        .shortDescription(ORIGINAL_EVENT_NAME).longDescription(DESCRIPTION).build();
    var rfxResponse = new ExportRfxResponse();
    rfxResponse.setRfxSetting(rfxSetting);

    // Mock behaviours
    when(validationService.validateProjectAndEventIds(PROC_PROJECT_ID, PROC_EVENT_ID))
        .thenReturn(procurementEvent);
    when(jaggaerService.getRfx(RFX_ID)).thenReturn(rfxResponse);

    var eventDetail = procurementEventService.getEvent(PROC_PROJECT_ID, PROC_EVENT_ID);

    // Verify
    assertEquals(eventType, eventDetail.getNonOCDS().getEventType());
    assertEquals(RFX_REF_CODE, eventDetail.getNonOCDS().getEventSupportId());

    assertEquals(RFX_ID, eventDetail.getOCDS().getId());
    assertEquals(ORIGINAL_EVENT_NAME, eventDetail.getOCDS().getTitle());
    assertEquals(DESCRIPTION, eventDetail.getOCDS().getDescription());
    assertEquals(TenderStatus.PLANNED, eventDetail.getOCDS().getStatus());
    assertEquals(AwardCriteria.RATEDCRITERIA, eventDetail.getOCDS().getAwardCriteria());

    return eventDetail;
  }

  @Test
  void testPublishEvent() throws Exception {

    var publishDates = mock(PublishDates.class);

    var documentData1 = new byte[] {'a', 'b', 'c'};
    var documentData2 = new byte[] {'1', '2', '3'};

    var procurementProject =
        ProcurementProject.builder().caNumber(CA_NUMBER).lotNumber(LOT_NUMBER).build();
    var procurementEvent = ProcurementEvent.builder().project(procurementProject).eventType("RFI")
        .externalEventId(RFX_ID).externalReferenceId(RFX_REF_CODE).build();

    var documentUpload1 = DocumentUpload.builder().id(1).externalStatus(VirusCheckStatus.SAFE)
        .documentId("YnV5ZXItMjM3MDU4LW5pY2VwZGYucGRm").mimetype("application/pdf")
        .documentDescription("A PDF").audience(DocumentAudienceType.BUYER).build();

    var documentUpload2 = DocumentUpload.builder().id(2).externalStatus(VirusCheckStatus.SAFE)
        .documentId("c3VwcGxpZXItNjU5MzUtbmljZXBuZy5wbmc=").mimetype("image/png")
        .documentDescription("A PNG").audience(DocumentAudienceType.SUPPLIER).build();

    // This should not be uploaded to Jaggaer
    var documentUpload3 = DocumentUpload.builder().id(2).externalStatus(VirusCheckStatus.UNSAFE)
        .documentId("c3VwcGxpZXItNjZ5MzUtbmljZXBuZy5wbmc=").mimetype("image/gif")
        .documentDescription("A GIF").audience(DocumentAudienceType.SUPPLIER).build();

    var documentUploads = Set.of(documentUpload1, documentUpload2, documentUpload3);
    procurementEvent.setDocumentUploads(documentUploads);
    documentUpload1.setProcurementEvent(procurementEvent);
    documentUpload2.setProcurementEvent(procurementEvent);
    documentUpload3.setProcurementEvent(procurementEvent);

    var rfxSetting = RfxSetting.builder().statusCode(100).rfxId(RFX_ID)
        .shortDescription(ORIGINAL_EVENT_NAME).longDescription(DESCRIPTION).build();
    var rfxResponse = new ExportRfxResponse();
    rfxResponse.setRfxSetting(rfxSetting);

    // Mock behaviours
    when(userProfileService.resolveBuyerUserByEmail(PRINCIPAL)).thenReturn(JAGGAER_USER);
    when(jaggaerService.getRfx(RFX_ID)).thenReturn(rfxResponse);
    when(validationService.validateProjectAndEventIds(PROC_PROJECT_ID, PROC_EVENT_ID))
        .thenReturn(procurementEvent);
    when(documentUploadService.retrieveDocument(documentUpload1, PRINCIPAL))
        .thenReturn(documentData1);
    when(documentUploadService.retrieveDocument(documentUpload2, PRINCIPAL))
        .thenReturn(documentData2);

    // Invoke & assert
    procurementEventService.publishEvent(PROC_PROJECT_ID, PROC_EVENT_ID, publishDates, PRINCIPAL);

    verify(jaggaerService, times(2)).eventUploadDocument(any(), any(), any(), any(), any());
    verify(jaggaerService).publishRfx(procurementEvent, publishDates, JAGGAER_USER_ID);
  }

  @Test
  void testPublishEventAlreadyPublished() throws Exception {

    var procurementProject =
        ProcurementProject.builder().caNumber(CA_NUMBER).lotNumber(LOT_NUMBER).build();
    var procurementEvent = ProcurementEvent.builder().project(procurementProject).eventType("RFI")
        .externalEventId(RFX_ID).externalReferenceId(RFX_REF_CODE).build();

    var rfxSetting = RfxSetting.builder().statusCode(300).rfxId(RFX_ID)
        .shortDescription(ORIGINAL_EVENT_NAME).longDescription(DESCRIPTION).build();
    var rfxResponse = new ExportRfxResponse();
    rfxResponse.setRfxSetting(rfxSetting);

    // Mock behaviours
    when(userProfileService.resolveBuyerUserByEmail(PRINCIPAL)).thenReturn(JAGGAER_USER);
    when(jaggaerService.getRfx(RFX_ID)).thenReturn(rfxResponse);
    when(validationService.validateProjectAndEventIds(PROC_PROJECT_ID, PROC_EVENT_ID))
        .thenReturn(procurementEvent);

    // Invoke & assert
    var ex = assertThrows(IllegalArgumentException.class, () -> procurementEventService
        .publishEvent(PROC_PROJECT_ID, PROC_EVENT_ID, null, PRINCIPAL));
    assertEquals("You cannot publish an event unless it is in a 'planned' state", ex.getMessage());
  }

  @Test
  void testGetEventsForProject() throws Exception {

    var event = new ProcurementEvent();
    event.setId(PROC_EVENT_DB_ID);
    event.setExternalEventId(RFX_ID);
    event.setExternalReferenceId(RFX_REF_CODE);
    event.setEventName("NAME");
    event.setEventType("RFI");
    event.setOcdsAuthorityName(OCDS_AUTH_NAME);
    event.setOcidPrefix(OCID_PREFIX);
    event.setAssessmentId(ASSESSMENT_ID);
    var events = Set.of(event);

    var rfxResponse = new ExportRfxResponse();
    var rfxSetting = RfxSetting.builder().statusCode(300).rfxId(RFX_ID).build();
    rfxResponse.setRfxSetting(rfxSetting);

    // Mock behaviours
    when(procurementEventRepo.findByProjectId(PROC_PROJECT_ID)).thenReturn(events);
    when(jaggaerService.getRfx(RFX_ID)).thenReturn(rfxResponse);

    var response = procurementEventService.getEventsForProject(PROC_PROJECT_ID, PRINCIPAL);

    // Verify
    assertEquals(1, response.size());

    var eventSummary = response.stream().findFirst().get();
    assertEquals("NAME", eventSummary.getTitle());
    assertEquals(ViewEventType.RFI, eventSummary.getEventType());
    assertEquals(TenderStatus.ACTIVE, eventSummary.getStatus());
    assertEquals(RFX_REF_CODE, eventSummary.getEventSupportId());
    assertEquals("ocds-b5fd17-2", eventSummary.getId());
    assertEquals(ASSESSMENT_ID, eventSummary.getAssessmentId());
  }

  @Test
  void testExtendEvent() throws Exception {
    var extendCriteria = new ExtendCriteria().endDate(OffsetDateTime.now());

    var procurementProject =
        ProcurementProject.builder().caNumber(CA_NUMBER).lotNumber(LOT_NUMBER).build();
    var procurementEvent = ProcurementEvent.builder().project(procurementProject).eventType("RFI")
        .externalEventId(RFX_ID).externalReferenceId(RFX_REF_CODE).build();

    var rfxSetting = RfxSetting.builder().statusCode(800).rfxId(RFX_ID)
        .shortDescription(ORIGINAL_EVENT_NAME).longDescription(DESCRIPTION).build();
    var rfxResponse = new ExportRfxResponse();
    rfxResponse.setRfxSetting(rfxSetting);

    var response = new CreateUpdateRfxResponse();
    response.setRfxId(RFX_ID);

    // Mock behaviours
    when(userProfileService.resolveBuyerUserByEmail(PRINCIPAL)).thenReturn(JAGGAER_USER);
    when(jaggaerService.getRfx(RFX_ID)).thenReturn(rfxResponse);
    when(validationService.validateProjectAndEventIds(PROC_PROJECT_ID, PROC_EVENT_ID))
        .thenReturn(procurementEvent);
    when(jaggaerService.extendRfx(any(), any())).thenReturn(response);

    // Invoke & assert
    var extendResponse = procurementEventService.extendEvent(PROC_PROJECT_ID, PROC_EVENT_ID,
        extendCriteria, PRINCIPAL);
    assertEquals(response.getRfxId(), extendResponse.getRfxId());
  }

  @Test
  void testSupplierResponses() throws Exception {

    var event = new ProcurementEvent();
    event.setId(PROC_EVENT_DB_ID);
    event.setExternalEventId(PROC_EVENT_ID);
    event.setEventType(ORIGINAL_EVENT_TYPE);

    var rfxResponse = new ExportRfxResponse();
    var rfxSetting = RfxSetting.builder().statusCode(0).rfxId(RFX_ID).build();

    var supplier = Supplier.builder()
        .companyData(CompanyData.builder().id(5684804).name("Test Supplier 1").build())
        .status("Replied").build();

    var organisationMapping =
        OrganisationMapping.builder().organisationId("GB-COH-05684804").build();
    rfxResponse.setRfxSetting(rfxSetting);
    rfxResponse.setSuppliersList(SuppliersList.builder().supplier(Arrays.asList(supplier)).build());

    // Mock behaviours
    when(validationService.validateProjectAndEventIds(PROC_PROJECT_ID, PROC_EVENT_ID))
        .thenReturn(event);
    when(jaggaerService.getRfx(PROC_EVENT_ID)).thenReturn(rfxResponse);
    when(organisationMappingRepo.findByExternalOrganisationId(supplier.getCompanyData().getId()))
        .thenReturn(Optional.of(organisationMapping));

    var response = procurementEventService.getSupplierResponses(PROC_PROJECT_ID, PROC_EVENT_ID);

    // Verify
    assertEquals(1, response.size());

    var responseSummary = response.stream().findFirst().get();
    assertEquals("GB-COH-05684804", responseSummary.getSupplier().getId());
    assertEquals("Test Supplier 1", responseSummary.getSupplier().getName());
    assertEquals(ResponseSummary.ResponseStateEnum.SUBMITTED, responseSummary.getResponseState());
    assertEquals(ResponseSummary.ReadStateEnum.READ, responseSummary.getReadState());
  }

  void testTerminateEvent() throws Exception {

    var procurementProject =
        ProcurementProject.builder().caNumber(CA_NUMBER).lotNumber(LOT_NUMBER).build();
    var procurementEvent = ProcurementEvent.builder().project(procurementProject).eventType("RFI")
        .externalEventId(RFX_ID).externalReferenceId(RFX_REF_CODE).build();
    var rfxSetting = RfxSetting.builder().statusCode(800).rfxId(RFX_ID)
        .shortDescription(ORIGINAL_EVENT_NAME).longDescription(DESCRIPTION).build();
    var rfxResponse = new ExportRfxResponse();
    rfxResponse.setRfxSetting(rfxSetting);

    var request =
        InvalidateEventRequest.builder().invalidateReason(TerminationType.CANCELLED.getValue())
            .rfxId(procurementEvent.getExternalEventId())
            .rfxReferenceCode(procurementEvent.getExternalReferenceId())
            .operatorUser(OwnerUser.builder().id(JAGGAER_USER_ID).build()).build();

    // Mock behaviours
    when(userProfileService.resolveBuyerUserByEmail(PRINCIPAL)).thenReturn(JAGGAER_USER);
    when(jaggaerService.getRfx(RFX_ID)).thenReturn(rfxResponse);
    when(validationService.validateProjectAndEventIds(PROC_PROJECT_ID, PROC_EVENT_ID))
        .thenReturn(procurementEvent);

    // Invoke
    procurementEventService.terminateEvent(PROC_PROJECT_ID, PROC_EVENT_ID,
        TerminationType.CANCELLED, PRINCIPAL);

    // Verify
    verify(jaggaerService).invalidateEvent(request);
  }

  @Test
  void shouldReturnClosedDashboardStatusForGetEventClosedEvent() throws Exception {
    var procurementEventBuilder = ProcurementEvent.builder().assessmentId(ASSESSMENT_ID)
            .assessmentSupplierTarget(ASSESSMENT_SUPPLIER_TARGET).tenderStatus("CLOSED");

    var eventDetail = testGetEventHelper(ViewEventType.FCA, procurementEventBuilder);

    assertEquals(ASSESSMENT_ID, eventDetail.getNonOCDS().getAssessmentId());
    assertEquals(ASSESSMENT_SUPPLIER_TARGET,
            eventDetail.getNonOCDS().getAssessmentSupplierTarget());

    assertEquals(DashboardStatus.CLOSED, eventDetail.getNonOCDS().getDashboardStatus());
    verify(criteriaService, never()).getEvalCriteria(anyInt(), anyString(), anyBoolean());
  }

  @Test
  void shouldReturnCompletedDashboardStatusForGetEventCompleteEvent() throws Exception {
    var procurementEventBuilder = ProcurementEvent.builder().assessmentId(ASSESSMENT_ID)
            .assessmentSupplierTarget(ASSESSMENT_SUPPLIER_TARGET).tenderStatus("COMPLETE");

    var eventDetail = testGetEventHelper(ViewEventType.FCA, procurementEventBuilder);

    assertEquals(ASSESSMENT_ID, eventDetail.getNonOCDS().getAssessmentId());
    assertEquals(ASSESSMENT_SUPPLIER_TARGET,
            eventDetail.getNonOCDS().getAssessmentSupplierTarget());

    assertEquals(DashboardStatus.COMPLETE, eventDetail.getNonOCDS().getDashboardStatus());
    verify(criteriaService, never()).getEvalCriteria(anyInt(), anyString(), anyBoolean());
  }

  @Test
  void shouldReturnAssessmentDashboardStatusForGetEventForDBEventTypeFCAWithNonCompleteNonClosedEvent() throws Exception {
    var procurementEventBuilder = ProcurementEvent.builder().assessmentId(ASSESSMENT_ID)
            .assessmentSupplierTarget(ASSESSMENT_SUPPLIER_TARGET).tenderStatus("PLANNED");

    var eventDetail = testGetEventHelper(ViewEventType.FCA, procurementEventBuilder);

    assertEquals(ASSESSMENT_ID, eventDetail.getNonOCDS().getAssessmentId());
    assertEquals(ASSESSMENT_SUPPLIER_TARGET,
            eventDetail.getNonOCDS().getAssessmentSupplierTarget());

    assertEquals(DashboardStatus.ASSESSMENT, eventDetail.getNonOCDS().getDashboardStatus());
    verify(criteriaService, never()).getEvalCriteria(anyInt(), anyString(), anyBoolean());
  }


  @Test
  void shouldReturnInProgressDashboardStatusForGetEventForNonDBEventTypeWithPublishDateNull() throws Exception {
    var procurementEventBuilder = ProcurementEvent.builder().assessmentId(ASSESSMENT_ID)
            .assessmentSupplierTarget(ASSESSMENT_SUPPLIER_TARGET).tenderStatus("NONNULL");

    var rfxSetting = RfxSetting.builder().rfxId(RFX_ID)
            .shortDescription(ORIGINAL_EVENT_NAME).longDescription(DESCRIPTION).publishDate(null).build();

    var procurementProject =
            ProcurementProject.builder().caNumber(CA_NUMBER).lotNumber(LOT_NUMBER).build();
    var procurementEvent =
            procurementEventBuilder.project(procurementProject).eventType(ViewEventType.FC.name())
                    .externalEventId(RFX_ID).externalReferenceId(RFX_REF_CODE).build();

    var rfxResponse = new ExportRfxResponse();
    rfxResponse.setRfxSetting(rfxSetting);

    // Mock behaviours
    when(validationService.validateProjectAndEventIds(PROC_PROJECT_ID, PROC_EVENT_ID))
            .thenReturn(procurementEvent);
    when(jaggaerService.getRfx(RFX_ID)).thenReturn(rfxResponse);

    var eventDetail = procurementEventService.getEvent(PROC_PROJECT_ID, PROC_EVENT_ID);

    assertEquals(ASSESSMENT_ID, eventDetail.getNonOCDS().getAssessmentId());
    assertEquals(ASSESSMENT_SUPPLIER_TARGET,
            eventDetail.getNonOCDS().getAssessmentSupplierTarget());

    assertEquals(DashboardStatus.IN_PROGRESS, eventDetail.getNonOCDS().getDashboardStatus());

  }


  @Test
  void shouldReturnPublishedDashboardStatusForGetEventForNonDBEventTypeWithClosedDateAfterToday() throws Exception {
    var procurementEventBuilder = ProcurementEvent.builder().assessmentId(ASSESSMENT_ID)
            .assessmentSupplierTarget(ASSESSMENT_SUPPLIER_TARGET).tenderStatus("NONNULL");

    var rfxSetting = RfxSetting.builder().rfxId(RFX_ID)
            .shortDescription(ORIGINAL_EVENT_NAME).longDescription(DESCRIPTION).publishDate(OffsetDateTime.now().minus(3, ChronoUnit.DAYS))
                      .closeDate(OffsetDateTime.now().plus(3, ChronoUnit.DAYS)).build();

    var procurementProject =
            ProcurementProject.builder().caNumber(CA_NUMBER).lotNumber(LOT_NUMBER).build();
    var procurementEvent =
            procurementEventBuilder.project(procurementProject).eventType(ViewEventType.FC.name())
                    .externalEventId(RFX_ID).externalReferenceId(RFX_REF_CODE).build();

    var rfxResponse = new ExportRfxResponse();
    rfxResponse.setRfxSetting(rfxSetting);

    // Mock behaviours
    when(validationService.validateProjectAndEventIds(PROC_PROJECT_ID, PROC_EVENT_ID))
            .thenReturn(procurementEvent);
    when(jaggaerService.getRfx(RFX_ID)).thenReturn(rfxResponse);

    var eventDetail = procurementEventService.getEvent(PROC_PROJECT_ID, PROC_EVENT_ID);

    assertEquals(ASSESSMENT_ID, eventDetail.getNonOCDS().getAssessmentId());
    assertEquals(ASSESSMENT_SUPPLIER_TARGET,
            eventDetail.getNonOCDS().getAssessmentSupplierTarget());

    assertEquals(DashboardStatus.PUBLISHED, eventDetail.getNonOCDS().getDashboardStatus());
  }

  @Test
  void shouldReturnToBeEvaluatedDashboardStatusForGetEventForNonDBEventTypeWithJaggaerStatusToBeEvaluated() throws Exception {
    var procurementEventBuilder = ProcurementEvent.builder().assessmentId(ASSESSMENT_ID)
            .assessmentSupplierTarget(ASSESSMENT_SUPPLIER_TARGET).tenderStatus("NONNULL");

    var rfxSetting = RfxSetting.builder().rfxId(RFX_ID)
            .shortDescription(ORIGINAL_EVENT_NAME).longDescription(DESCRIPTION).publishDate(OffsetDateTime.now().minus(3, ChronoUnit.DAYS))
            .closeDate(OffsetDateTime.now().minus(1, ChronoUnit.DAYS)).status("To Be Evaluated").build();

    var procurementProject =
            ProcurementProject.builder().caNumber(CA_NUMBER).lotNumber(LOT_NUMBER).build();
    var procurementEvent =
            procurementEventBuilder.project(procurementProject).eventType(ViewEventType.FC.name())
                    .externalEventId(RFX_ID).externalReferenceId(RFX_REF_CODE).build();

    var rfxResponse = new ExportRfxResponse();
    rfxResponse.setRfxSetting(rfxSetting);

    // Mock behaviours
    when(validationService.validateProjectAndEventIds(PROC_PROJECT_ID, PROC_EVENT_ID))
            .thenReturn(procurementEvent);
    when(jaggaerService.getRfx(RFX_ID)).thenReturn(rfxResponse);

    var eventDetail = procurementEventService.getEvent(PROC_PROJECT_ID, PROC_EVENT_ID);

    assertEquals(ASSESSMENT_ID, eventDetail.getNonOCDS().getAssessmentId());
    assertEquals(ASSESSMENT_SUPPLIER_TARGET,
            eventDetail.getNonOCDS().getAssessmentSupplierTarget());

    assertEquals(DashboardStatus.TO_BE_EVALUATED, eventDetail.getNonOCDS().getDashboardStatus());
  }


  // Evaluating status test cases
  @Test
  void shouldReturnEvaluatingDashboardStatusForGetEventForNonDBEventTypeWithJaggaerStatusQualificationEvaluation() throws Exception {
    var procurementEventBuilder = ProcurementEvent.builder().assessmentId(ASSESSMENT_ID)
            .assessmentSupplierTarget(ASSESSMENT_SUPPLIER_TARGET).tenderStatus("NONNULL");

    var rfxSetting = RfxSetting.builder().rfxId(RFX_ID)
            .shortDescription(ORIGINAL_EVENT_NAME).longDescription(DESCRIPTION).publishDate(OffsetDateTime.now().minus(3, ChronoUnit.DAYS))
            .closeDate(OffsetDateTime.now().minus(1, ChronoUnit.DAYS)).status("Qualification Evaluation").build();

    var procurementProject =
            ProcurementProject.builder().caNumber(CA_NUMBER).lotNumber(LOT_NUMBER).build();
    var procurementEvent =
            procurementEventBuilder.project(procurementProject).eventType(ViewEventType.FC.name())
                    .externalEventId(RFX_ID).externalReferenceId(RFX_REF_CODE).build();

    var rfxResponse = new ExportRfxResponse();
    rfxResponse.setRfxSetting(rfxSetting);

    // Mock behaviours
    when(validationService.validateProjectAndEventIds(PROC_PROJECT_ID, PROC_EVENT_ID))
            .thenReturn(procurementEvent);
    when(jaggaerService.getRfx(RFX_ID)).thenReturn(rfxResponse);

    var eventDetail = procurementEventService.getEvent(PROC_PROJECT_ID, PROC_EVENT_ID);

    assertEquals(ASSESSMENT_ID, eventDetail.getNonOCDS().getAssessmentId());
    assertEquals(ASSESSMENT_SUPPLIER_TARGET,
            eventDetail.getNonOCDS().getAssessmentSupplierTarget());

    assertEquals(DashboardStatus.EVALUATING, eventDetail.getNonOCDS().getDashboardStatus());
  }

  @Test
  void shouldReturnEvaluatingDashboardStatusForGetEventForNonDBEventTypeWithJaggaerStatusTechnicalEvaluation() throws Exception {
    var procurementEventBuilder = ProcurementEvent.builder().assessmentId(ASSESSMENT_ID)
            .assessmentSupplierTarget(ASSESSMENT_SUPPLIER_TARGET).tenderStatus("NONNULL");

    var rfxSetting = RfxSetting.builder().rfxId(RFX_ID)
            .shortDescription(ORIGINAL_EVENT_NAME).longDescription(DESCRIPTION).publishDate(OffsetDateTime.now().minus(3, ChronoUnit.DAYS))
            .closeDate(OffsetDateTime.now().minus(1, ChronoUnit.DAYS)).status("Technical Evaluation").build();

    var procurementProject =
            ProcurementProject.builder().caNumber(CA_NUMBER).lotNumber(LOT_NUMBER).build();
    var procurementEvent =
            procurementEventBuilder.project(procurementProject).eventType(ViewEventType.FC.name())
                    .externalEventId(RFX_ID).externalReferenceId(RFX_REF_CODE).build();

    var rfxResponse = new ExportRfxResponse();
    rfxResponse.setRfxSetting(rfxSetting);

    // Mock behaviours
    when(validationService.validateProjectAndEventIds(PROC_PROJECT_ID, PROC_EVENT_ID))
            .thenReturn(procurementEvent);
    when(jaggaerService.getRfx(RFX_ID)).thenReturn(rfxResponse);

    var eventDetail = procurementEventService.getEvent(PROC_PROJECT_ID, PROC_EVENT_ID);

    assertEquals(ASSESSMENT_ID, eventDetail.getNonOCDS().getAssessmentId());
    assertEquals(ASSESSMENT_SUPPLIER_TARGET,
            eventDetail.getNonOCDS().getAssessmentSupplierTarget());

    assertEquals(DashboardStatus.EVALUATING, eventDetail.getNonOCDS().getDashboardStatus());
  }

  @Test
  void shouldReturnEvaluatingdDashboardStatusForGetEventForNonDBEventTypeWithJaggaerStatusCommercialEvaluation() throws Exception {
    var procurementEventBuilder = ProcurementEvent.builder().assessmentId(ASSESSMENT_ID)
            .assessmentSupplierTarget(ASSESSMENT_SUPPLIER_TARGET).tenderStatus("NONNULL");

    var rfxSetting = RfxSetting.builder().rfxId(RFX_ID)
            .shortDescription(ORIGINAL_EVENT_NAME).longDescription(DESCRIPTION).publishDate(OffsetDateTime.now().minus(3, ChronoUnit.DAYS))
            .closeDate(OffsetDateTime.now().minus(1, ChronoUnit.DAYS)).status("Commercial Evaluation").build();

    var procurementProject =
            ProcurementProject.builder().caNumber(CA_NUMBER).lotNumber(LOT_NUMBER).build();
    var procurementEvent =
            procurementEventBuilder.project(procurementProject).eventType(ViewEventType.FC.name())
                    .externalEventId(RFX_ID).externalReferenceId(RFX_REF_CODE).build();

    var rfxResponse = new ExportRfxResponse();
    rfxResponse.setRfxSetting(rfxSetting);

    // Mock behaviours
    when(validationService.validateProjectAndEventIds(PROC_PROJECT_ID, PROC_EVENT_ID))
            .thenReturn(procurementEvent);
    when(jaggaerService.getRfx(RFX_ID)).thenReturn(rfxResponse);

    var eventDetail = procurementEventService.getEvent(PROC_PROJECT_ID, PROC_EVENT_ID);

    assertEquals(ASSESSMENT_ID, eventDetail.getNonOCDS().getAssessmentId());
    assertEquals(ASSESSMENT_SUPPLIER_TARGET,
            eventDetail.getNonOCDS().getAssessmentSupplierTarget());

    assertEquals(DashboardStatus.EVALUATING, eventDetail.getNonOCDS().getDashboardStatus());
  }


  @Test
  void shouldReturnEvaluatingDashboardStatusForGetEventForNonDBEventTypeWithJaggaerStatusBestAndFinalOfferEvaluation() throws Exception {
    var procurementEventBuilder = ProcurementEvent.builder().assessmentId(ASSESSMENT_ID)
            .assessmentSupplierTarget(ASSESSMENT_SUPPLIER_TARGET).tenderStatus("NONNULL");

    var rfxSetting = RfxSetting.builder().rfxId(RFX_ID)
            .shortDescription(ORIGINAL_EVENT_NAME).longDescription(DESCRIPTION).publishDate(OffsetDateTime.now().minus(3, ChronoUnit.DAYS))
            .closeDate(OffsetDateTime.now().minus(1, ChronoUnit.DAYS)).status("Best and Final Offer Evaluation").build();

    var procurementProject =
            ProcurementProject.builder().caNumber(CA_NUMBER).lotNumber(LOT_NUMBER).build();
    var procurementEvent =
            procurementEventBuilder.project(procurementProject).eventType(ViewEventType.FC.name())
                    .externalEventId(RFX_ID).externalReferenceId(RFX_REF_CODE).build();

    var rfxResponse = new ExportRfxResponse();
    rfxResponse.setRfxSetting(rfxSetting);

    // Mock behaviours
    when(validationService.validateProjectAndEventIds(PROC_PROJECT_ID, PROC_EVENT_ID))
            .thenReturn(procurementEvent);
    when(jaggaerService.getRfx(RFX_ID)).thenReturn(rfxResponse);

    var eventDetail = procurementEventService.getEvent(PROC_PROJECT_ID, PROC_EVENT_ID);

    assertEquals(ASSESSMENT_ID, eventDetail.getNonOCDS().getAssessmentId());
    assertEquals(ASSESSMENT_SUPPLIER_TARGET,
            eventDetail.getNonOCDS().getAssessmentSupplierTarget());

    assertEquals(DashboardStatus.EVALUATING, eventDetail.getNonOCDS().getDashboardStatus());
  }

  @Test
  void shouldReturnEvaluatingDashboardStatusForGetEventForNonDBEventTypeWithJaggaerStatusTIOCClosed() throws Exception {
    var procurementEventBuilder = ProcurementEvent.builder().assessmentId(ASSESSMENT_ID)
            .assessmentSupplierTarget(ASSESSMENT_SUPPLIER_TARGET).tenderStatus("NONNULL");

    var rfxSetting = RfxSetting.builder().rfxId(RFX_ID)
            .shortDescription(ORIGINAL_EVENT_NAME).longDescription(DESCRIPTION).publishDate(OffsetDateTime.now().minus(3, ChronoUnit.DAYS))
            .closeDate(OffsetDateTime.now().minus(1, ChronoUnit.DAYS)).status("TIOC Closed").build();

    var procurementProject =
            ProcurementProject.builder().caNumber(CA_NUMBER).lotNumber(LOT_NUMBER).build();
    var procurementEvent =
            procurementEventBuilder.project(procurementProject).eventType(ViewEventType.FC.name())
                    .externalEventId(RFX_ID).externalReferenceId(RFX_REF_CODE).build();

    var rfxResponse = new ExportRfxResponse();
    rfxResponse.setRfxSetting(rfxSetting);

    // Mock behaviours
    when(validationService.validateProjectAndEventIds(PROC_PROJECT_ID, PROC_EVENT_ID))
            .thenReturn(procurementEvent);
    when(jaggaerService.getRfx(RFX_ID)).thenReturn(rfxResponse);

    var eventDetail = procurementEventService.getEvent(PROC_PROJECT_ID, PROC_EVENT_ID);

    assertEquals(ASSESSMENT_ID, eventDetail.getNonOCDS().getAssessmentId());
    assertEquals(ASSESSMENT_SUPPLIER_TARGET,
            eventDetail.getNonOCDS().getAssessmentSupplierTarget());

    assertEquals(DashboardStatus.EVALUATING, eventDetail.getNonOCDS().getDashboardStatus());
  }

  @Test
  void shouldReturnEvaluatedDashboardStatusForGetEventForNonDBEventTypeWithJaggaerStatusFinalEvaluation() throws Exception {
    var procurementEventBuilder = ProcurementEvent.builder().assessmentId(ASSESSMENT_ID)
            .assessmentSupplierTarget(ASSESSMENT_SUPPLIER_TARGET).tenderStatus("NONNULL");

    var rfxSetting = RfxSetting.builder().rfxId(RFX_ID)
            .shortDescription(ORIGINAL_EVENT_NAME).longDescription(DESCRIPTION).publishDate(OffsetDateTime.now().minus(3, ChronoUnit.DAYS))
            .closeDate(OffsetDateTime.now().minus(1, ChronoUnit.DAYS)).status("Final Evaluation").build();

    var procurementProject =
            ProcurementProject.builder().caNumber(CA_NUMBER).lotNumber(LOT_NUMBER).build();
    var procurementEvent =
            procurementEventBuilder.project(procurementProject).eventType(ViewEventType.FC.name())
                    .externalEventId(RFX_ID).externalReferenceId(RFX_REF_CODE).build();

    var rfxResponse = new ExportRfxResponse();
    rfxResponse.setRfxSetting(rfxSetting);

    // Mock behaviours
    when(validationService.validateProjectAndEventIds(PROC_PROJECT_ID, PROC_EVENT_ID))
            .thenReturn(procurementEvent);
    when(jaggaerService.getRfx(RFX_ID)).thenReturn(rfxResponse);

    var eventDetail = procurementEventService.getEvent(PROC_PROJECT_ID, PROC_EVENT_ID);

    assertEquals(ASSESSMENT_ID, eventDetail.getNonOCDS().getAssessmentId());
    assertEquals(ASSESSMENT_SUPPLIER_TARGET,
            eventDetail.getNonOCDS().getAssessmentSupplierTarget());

    assertEquals(DashboardStatus.EVALUATED, eventDetail.getNonOCDS().getDashboardStatus());
  }

  @Test
  void shouldReturnPreAwardDashboardStatusForGetEventForNonDBEventTypeWithJaggaerStatusFinalEvaluationPreAwarded() throws Exception {
    var procurementEventBuilder = ProcurementEvent.builder().assessmentId(ASSESSMENT_ID)
            .assessmentSupplierTarget(ASSESSMENT_SUPPLIER_TARGET).tenderStatus("NONNULL");

    var rfxSetting = RfxSetting.builder().rfxId(RFX_ID)
            .shortDescription(ORIGINAL_EVENT_NAME).longDescription(DESCRIPTION).publishDate(OffsetDateTime.now().minus(3, ChronoUnit.DAYS))
            .closeDate(OffsetDateTime.now().minus(1, ChronoUnit.DAYS)).status("Final Evaluation - Pre-Awarded").build();

    var procurementProject =
            ProcurementProject.builder().caNumber(CA_NUMBER).lotNumber(LOT_NUMBER).build();
    var procurementEvent =
            procurementEventBuilder.project(procurementProject).eventType(ViewEventType.FC.name())
                    .externalEventId(RFX_ID).externalReferenceId(RFX_REF_CODE).build();

    var rfxResponse = new ExportRfxResponse();
    rfxResponse.setRfxSetting(rfxSetting);

    // Mock behaviours
    when(validationService.validateProjectAndEventIds(PROC_PROJECT_ID, PROC_EVENT_ID))
            .thenReturn(procurementEvent);
    when(jaggaerService.getRfx(RFX_ID)).thenReturn(rfxResponse);

    var eventDetail = procurementEventService.getEvent(PROC_PROJECT_ID, PROC_EVENT_ID);

    assertEquals(ASSESSMENT_ID, eventDetail.getNonOCDS().getAssessmentId());
    assertEquals(ASSESSMENT_SUPPLIER_TARGET,
            eventDetail.getNonOCDS().getAssessmentSupplierTarget());

    assertEquals(DashboardStatus.PRE_AWARD, eventDetail.getNonOCDS().getDashboardStatus());
  }

  @Test
  void shouldReturnPreAwardDashboardStatusForGetEventForNonDBEventTypeWithJaggaerStatusAwardingApproval() throws Exception {
    var procurementEventBuilder = ProcurementEvent.builder().assessmentId(ASSESSMENT_ID)
            .assessmentSupplierTarget(ASSESSMENT_SUPPLIER_TARGET).tenderStatus("NONNULL");

    var rfxSetting = RfxSetting.builder().rfxId(RFX_ID)
            .shortDescription(ORIGINAL_EVENT_NAME).longDescription(DESCRIPTION).publishDate(OffsetDateTime.now().minus(3, ChronoUnit.DAYS))
            .closeDate(OffsetDateTime.now().minus(1, ChronoUnit.DAYS)).status("Awarding Approval").build();

    var procurementProject =
            ProcurementProject.builder().caNumber(CA_NUMBER).lotNumber(LOT_NUMBER).build();
    var procurementEvent =
            procurementEventBuilder.project(procurementProject).eventType(ViewEventType.FC.name())
                    .externalEventId(RFX_ID).externalReferenceId(RFX_REF_CODE).build();

    var rfxResponse = new ExportRfxResponse();
    rfxResponse.setRfxSetting(rfxSetting);

    // Mock behaviours
    when(validationService.validateProjectAndEventIds(PROC_PROJECT_ID, PROC_EVENT_ID))
            .thenReturn(procurementEvent);
    when(jaggaerService.getRfx(RFX_ID)).thenReturn(rfxResponse);

    var eventDetail = procurementEventService.getEvent(PROC_PROJECT_ID, PROC_EVENT_ID);

    assertEquals(ASSESSMENT_ID, eventDetail.getNonOCDS().getAssessmentId());
    assertEquals(ASSESSMENT_SUPPLIER_TARGET,
            eventDetail.getNonOCDS().getAssessmentSupplierTarget());

    assertEquals(DashboardStatus.PRE_AWARD, eventDetail.getNonOCDS().getDashboardStatus());
  }

  @Test
  void shouldReturnAwardedDashboardStatusForGetEventForNonDBEventTypeWithJaggaerStatusAwarded() throws Exception {
    var procurementEventBuilder = ProcurementEvent.builder().assessmentId(ASSESSMENT_ID)
            .assessmentSupplierTarget(ASSESSMENT_SUPPLIER_TARGET).tenderStatus("NONNULL");

    var rfxSetting = RfxSetting.builder().rfxId(RFX_ID)
            .shortDescription(ORIGINAL_EVENT_NAME).longDescription(DESCRIPTION).publishDate(OffsetDateTime.now().minus(3, ChronoUnit.DAYS))
            .closeDate(OffsetDateTime.now().minus(1, ChronoUnit.DAYS)).status("Awarded").build();

    var procurementProject =
            ProcurementProject.builder().caNumber(CA_NUMBER).lotNumber(LOT_NUMBER).build();
    var procurementEvent =
            procurementEventBuilder.project(procurementProject).eventType(ViewEventType.FC.name())
                    .externalEventId(RFX_ID).externalReferenceId(RFX_REF_CODE).build();

    var rfxResponse = new ExportRfxResponse();
    rfxResponse.setRfxSetting(rfxSetting);

    // Mock behaviours
    when(validationService.validateProjectAndEventIds(PROC_PROJECT_ID, PROC_EVENT_ID))
            .thenReturn(procurementEvent);
    when(jaggaerService.getRfx(RFX_ID)).thenReturn(rfxResponse);

    var eventDetail = procurementEventService.getEvent(PROC_PROJECT_ID, PROC_EVENT_ID);

    assertEquals(ASSESSMENT_ID, eventDetail.getNonOCDS().getAssessmentId());
    assertEquals(ASSESSMENT_SUPPLIER_TARGET,
            eventDetail.getNonOCDS().getAssessmentSupplierTarget());

    assertEquals(DashboardStatus.AWARDED, eventDetail.getNonOCDS().getDashboardStatus());
  }
  @Test
  void shouldReturnAwardedDashboardStatusForGetEventForNonDBEventTypeWithJaggaerStatusMixedAwarding() throws Exception {
    var procurementEventBuilder = ProcurementEvent.builder().assessmentId(ASSESSMENT_ID)
            .assessmentSupplierTarget(ASSESSMENT_SUPPLIER_TARGET).tenderStatus("NONNULL");

    var rfxSetting = RfxSetting.builder().rfxId(RFX_ID)
            .shortDescription(ORIGINAL_EVENT_NAME).longDescription(DESCRIPTION).publishDate(OffsetDateTime.now().minus(3, ChronoUnit.DAYS))
            .closeDate(OffsetDateTime.now().minus(1, ChronoUnit.DAYS)).status("Mixed Awarding").build();

    var procurementProject =
            ProcurementProject.builder().caNumber(CA_NUMBER).lotNumber(LOT_NUMBER).build();
    var procurementEvent =
            procurementEventBuilder.project(procurementProject).eventType(ViewEventType.FC.name())
                    .externalEventId(RFX_ID).externalReferenceId(RFX_REF_CODE).build();

    var rfxResponse = new ExportRfxResponse();
    rfxResponse.setRfxSetting(rfxSetting);

    // Mock behaviours
    when(validationService.validateProjectAndEventIds(PROC_PROJECT_ID, PROC_EVENT_ID))
            .thenReturn(procurementEvent);
    when(jaggaerService.getRfx(RFX_ID)).thenReturn(rfxResponse);

    var eventDetail = procurementEventService.getEvent(PROC_PROJECT_ID, PROC_EVENT_ID);

    assertEquals(ASSESSMENT_ID, eventDetail.getNonOCDS().getAssessmentId());
    assertEquals(ASSESSMENT_SUPPLIER_TARGET,
            eventDetail.getNonOCDS().getAssessmentSupplierTarget());

    assertEquals(DashboardStatus.AWARDED, eventDetail.getNonOCDS().getDashboardStatus());
  }

  @Test
  void shouldReturnCloseDashboardStatusForGetEventsForProjectForEventWithCloseStatus() throws Exception {

    var event = new ProcurementEvent();
    event.setId(PROC_EVENT_DB_ID);
    event.setExternalEventId(RFX_ID);
    event.setExternalReferenceId(RFX_REF_CODE);
    event.setEventName("NAME");
    event.setEventType("RFI");
    event.setOcdsAuthorityName(OCDS_AUTH_NAME);
    event.setOcidPrefix(OCID_PREFIX);
    event.setAssessmentId(ASSESSMENT_ID);
    event.setTenderStatus("CLOSED");
    var events = Set.of(event);

    var rfxResponse = new ExportRfxResponse();
    var rfxSetting = RfxSetting.builder().statusCode(300).rfxId(RFX_ID).build();
    rfxResponse.setRfxSetting(rfxSetting);

    // Mock behaviours
    when(procurementEventRepo.findByProjectId(PROC_PROJECT_ID)).thenReturn(events);
    when(jaggaerService.getRfx(RFX_ID)).thenReturn(rfxResponse);

    var response = procurementEventService.getEventsForProject(PROC_PROJECT_ID, PRINCIPAL);

    // Verify
    assertEquals(1, response.size());

    var eventSummary = response.stream().findFirst().get();
    assertEquals("NAME", eventSummary.getTitle());
    assertEquals(ViewEventType.RFI, eventSummary.getEventType());
    assertEquals(TenderStatus.ACTIVE, eventSummary.getStatus());
    assertEquals(RFX_REF_CODE, eventSummary.getEventSupportId());
    assertEquals("ocds-b5fd17-2", eventSummary.getId());
    assertEquals(ASSESSMENT_ID, eventSummary.getAssessmentId());
    assertEquals(DashboardStatus.CLOSED, eventSummary.getDashboardStatus());
  }

  @Test
  void shouldReturnCloseDashboardStatusForGetEventsForProjectForEventWithCompleteStatus() throws Exception {

    var event = new ProcurementEvent();
    event.setId(PROC_EVENT_DB_ID);
    event.setExternalEventId(RFX_ID);
    event.setExternalReferenceId(RFX_REF_CODE);
    event.setEventName("NAME");
    event.setEventType("RFI");
    event.setOcdsAuthorityName(OCDS_AUTH_NAME);
    event.setOcidPrefix(OCID_PREFIX);
    event.setAssessmentId(ASSESSMENT_ID);
    event.setTenderStatus("COMPLETE");
    var events = Set.of(event);

    var rfxResponse = new ExportRfxResponse();
    var rfxSetting = RfxSetting.builder().statusCode(300).rfxId(RFX_ID).build();
    rfxResponse.setRfxSetting(rfxSetting);

    // Mock behaviours
    when(procurementEventRepo.findByProjectId(PROC_PROJECT_ID)).thenReturn(events);
    when(jaggaerService.getRfx(RFX_ID)).thenReturn(rfxResponse);

    var response = procurementEventService.getEventsForProject(PROC_PROJECT_ID, PRINCIPAL);

    // Verify
    assertEquals(1, response.size());

    var eventSummary = response.stream().findFirst().get();
    assertEquals("NAME", eventSummary.getTitle());
    assertEquals(ViewEventType.RFI, eventSummary.getEventType());
    assertEquals(TenderStatus.ACTIVE, eventSummary.getStatus());
    assertEquals(RFX_REF_CODE, eventSummary.getEventSupportId());
    assertEquals("ocds-b5fd17-2", eventSummary.getId());
    assertEquals(ASSESSMENT_ID, eventSummary.getAssessmentId());
    assertEquals(DashboardStatus.COMPLETE, eventSummary.getDashboardStatus());
  }

  @Test
  void shouldReturnAssessmentDashboardStatusForGetEventsForProjectForDBEventType() throws Exception {

    var event = new ProcurementEvent();
    event.setId(PROC_EVENT_DB_ID);
    event.setExternalEventId(RFX_ID);
    event.setExternalReferenceId(RFX_REF_CODE);
    event.setEventName("NAME");
    event.setEventType("FCA");
    event.setOcdsAuthorityName(OCDS_AUTH_NAME);
    event.setOcidPrefix(OCID_PREFIX);
    event.setAssessmentId(ASSESSMENT_ID);
    event.setTenderStatus("NONNULL");

    var events = Set.of(event);

    var rfxResponse = new ExportRfxResponse();
    var rfxSetting = RfxSetting.builder().statusCode(300).rfxId(RFX_ID).build();
    rfxResponse.setRfxSetting(rfxSetting);

    // Mock behaviours
    when(procurementEventRepo.findByProjectId(PROC_PROJECT_ID)).thenReturn(events);
    when(jaggaerService.getRfx(RFX_ID)).thenReturn(rfxResponse);

    var response = procurementEventService.getEventsForProject(PROC_PROJECT_ID, PRINCIPAL);

    // Verify
    assertEquals(1, response.size());

    var eventSummary = response.stream().findFirst().get();
    assertEquals("NAME", eventSummary.getTitle());
    assertEquals(ViewEventType.FCA, eventSummary.getEventType());
    assertEquals(DashboardStatus.ASSESSMENT, eventSummary.getDashboardStatus());
  }

  @Test
  void shouldReturnAwardedAssessmentDashboardStatusForGetEventsForProjectForNonDBEventTypeWithStatusAwarded() throws Exception {

    var event = new ProcurementEvent();
    event.setId(PROC_EVENT_DB_ID);
    event.setExternalEventId(RFX_ID);
    event.setExternalReferenceId(RFX_REF_CODE);
    event.setEventName("NAME");
    event.setEventType("RFI");
    event.setOcdsAuthorityName(OCDS_AUTH_NAME);
    event.setOcidPrefix(OCID_PREFIX);
    event.setAssessmentId(ASSESSMENT_ID);
    event.setTenderStatus("NONNULL");

    var events = Set.of(event);

    var rfxResponse = new ExportRfxResponse();
    var rfxSetting = RfxSetting.builder().publishDate(OffsetDateTime.now().minus(3, ChronoUnit.DAYS))
            .closeDate(OffsetDateTime.now().minus(1, ChronoUnit.DAYS)).status("Awarded").rfxId(RFX_ID).build();
    rfxResponse.setRfxSetting(rfxSetting);

    // Mock behaviours
    when(procurementEventRepo.findByProjectId(PROC_PROJECT_ID)).thenReturn(events);
    when(jaggaerService.getRfx(RFX_ID)).thenReturn(rfxResponse);

    var response = procurementEventService.getEventsForProject(PROC_PROJECT_ID, PRINCIPAL);

    // Verify
    assertEquals(1, response.size());

    var eventSummary = response.stream().findFirst().get();
    assertEquals("NAME", eventSummary.getTitle());
    assertEquals(ViewEventType.RFI, eventSummary.getEventType());
    assertEquals(DashboardStatus.AWARDED, eventSummary.getDashboardStatus());
  }
  private RfxSetting getRfxSetting() {
    var rfxSetting = RfxSetting.builder().statusCode(100).rfxId(RFX_ID)
            .shortDescription(ORIGINAL_EVENT_NAME).longDescription(DESCRIPTION).build();
    return rfxSetting;
  }
}
