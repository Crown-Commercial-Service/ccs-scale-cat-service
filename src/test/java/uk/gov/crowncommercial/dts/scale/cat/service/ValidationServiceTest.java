package uk.gov.crowncommercial.dts.scale.cat.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.exception.ResourceNotFoundException;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementProject;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.DefineEventType;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.PublishDates;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.QuestionNonOCDSOptions;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.UpdateEvent;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;
import uk.gov.crowncommercial.dts.scale.cat.service.ca.AssessmentService;

import jakarta.validation.ValidationException;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = {ValidationService.class}, webEnvironment = WebEnvironment.NONE)
@EnableConfigurationProperties(JaggaerAPIConfig.class)
class ValidationServiceTest {

  private static final Integer PROC_PROJECT_ID = 1;
  private static final String PROC_EVENT_ID = "ocds-pfhb7i-2";
  private static final String PROC_EVENT_INTERNAL_ID = "2";
  private static final String PROC_EVENT_AUTHORITY = "ocds";
  private static final String PROC_EVENT_PREFIX = "pfhb7i";
  private static final String PRINCIPAL = "jsmith@ccs.org.uk";

  @Autowired
  ValidationService validationService;

  @MockBean
  RetryableTendersDBDelegate retryableTendersDBDelegate;

  @MockBean
  AssessmentService assessmentService;

  @MockBean
  Clock clock;

  @Test
  void testValidateEventId_success() {
    var ocid = validationService.validateEventId(PROC_EVENT_ID);
    assertEquals(PROC_EVENT_AUTHORITY, ocid.getAuthority());
    assertEquals(PROC_EVENT_PREFIX, ocid.getPublisherPrefix());
    assertEquals(PROC_EVENT_INTERNAL_ID, ocid.getInternalId());
  }

  @Test
  void testValidateEventId_fail() {
    var ex = assertThrows(IllegalArgumentException.class,
        () -> validationService.validateEventId("EVENT-1"));
    assertEquals("Event ID 'EVENT-1' is not in the expected format", ex.getMessage());
  }

  @Test
  void testValidateProjectAndEventIds_success() {
    var project = new ProcurementProject();
    project.setId(PROC_PROJECT_ID);
    var event = new ProcurementEvent();
    event.setProject(project);

    when(retryableTendersDBDelegate.findProcurementEventByIdAndOcdsAuthorityNameAndOcidPrefix(
        Integer.valueOf(PROC_EVENT_INTERNAL_ID), PROC_EVENT_AUTHORITY, PROC_EVENT_PREFIX))
            .thenReturn(Optional.of(event));
    var response = validationService.validateProjectAndEventIds(PROC_PROJECT_ID, PROC_EVENT_ID);
    assertEquals(event, response);
  }

  @Test
  void testValidateProjectAndEventIds_projectMismatch() {
    var project = new ProcurementProject();
    // Project in DB does not match Project in Event ID
    project.setId(2);
    var event = new ProcurementEvent();
    event.setProject(project);

    when(retryableTendersDBDelegate.findProcurementEventByIdAndOcdsAuthorityNameAndOcidPrefix(
        Integer.valueOf(PROC_EVENT_INTERNAL_ID), PROC_EVENT_AUTHORITY, PROC_EVENT_PREFIX))
            .thenReturn(Optional.of(event));
    assertThrows(ResourceNotFoundException.class,
        () -> validationService.validateProjectAndEventIds(PROC_PROJECT_ID, PROC_EVENT_ID));
  }

  @Test
  void testValidateProjectAndEventIds_notFound() {
    when(retryableTendersDBDelegate.findProcurementEventByIdAndOcdsAuthorityNameAndOcidPrefix(
        Integer.valueOf(PROC_EVENT_INTERNAL_ID), PROC_EVENT_AUTHORITY, PROC_EVENT_PREFIX))
            .thenReturn(Optional.empty());
    assertThrows(ResourceNotFoundException.class,
        () -> validationService.validateProjectAndEventIds(PROC_PROJECT_ID, PROC_EVENT_ID));
  }

  @Test
  void testValidatePublishDates_valid() {
    var fixedInstant = Instant.parse("2022-01-06T13:00:00.00Z");

    ReflectionTestUtils.setField(validationService, "clock",
        Clock.fixed(fixedInstant, ZoneOffset.UTC));

    var endDate = OffsetDateTime.parse("2022-01-06T13:01:00.00Z");
    var publishDates = new PublishDates().endDate(endDate);
    validationService.validatePublishDates(publishDates);
  }

  @Test
  void testValidatePublishDates_endDate_invalid() {
    var fixedInstant = Instant.parse("2022-01-06T13:00:00.00Z");

    ReflectionTestUtils.setField(validationService, "clock",
        Clock.fixed(fixedInstant, ZoneOffset.UTC));

    var endDate = OffsetDateTime.parse("2022-01-06T12:59:59.999Z");
    var publishDates = new PublishDates().endDate(endDate);

    var ex = assertThrows(IllegalArgumentException.class,
        () -> validationService.validatePublishDates(publishDates));

    assertEquals("endDate must be in the future", ex.getMessage());
  }

  @Test
  void testValidateUpdateEventAssessment_ok() {
    var updateEvent = new UpdateEvent().assessmentId(1).assessmentSupplierTarget(10)
        .eventType(DefineEventType.FCA);

    var procurementEvent = ProcurementEvent.builder().eventType("FCA").build();

    validationService.validateUpdateEventAssessment(updateEvent, procurementEvent, PRINCIPAL);
  }

  @Test
  void testValidateUpdateEventAssessment_assessmentIdInvalidEventType() {
    var updateEvent = new UpdateEvent().assessmentId(1).assessmentSupplierTarget(10)
        .eventType(DefineEventType.RFI);
    var procurementEvent = new ProcurementEvent();

    var ex = assertThrows(ValidationException.class, () -> validationService
        .validateUpdateEventAssessment(updateEvent, procurementEvent, PRINCIPAL));

    assertEquals("assessmentId is invalid for eventType: RFI", ex.getMessage());
  }

  @Test
  void testValidateUpdateEventAssessment_assSupTgtMissingAssId() {
    var updateEvent = new UpdateEvent().assessmentSupplierTarget(10);
    var procurementEvent = ProcurementEvent.builder().eventType("FCA").build();

    var ex = assertThrows(ValidationException.class, () -> validationService
        .validateUpdateEventAssessment(updateEvent, procurementEvent, PRINCIPAL));

    assertEquals("assessmentId must be provided with assessmentSupplierTarget", ex.getMessage());
  }

  @Test
  void testValidateUpdateEventAssessment_assSupTgtInvalidExistingEventType() {
    var updateEvent = new UpdateEvent().assessmentId(1).assessmentSupplierTarget(10);

    var procurementEvent = ProcurementEvent.builder().eventType("RFI").build();

    var ex = assertThrows(ValidationException.class, () -> validationService
        .validateUpdateEventAssessment(updateEvent, procurementEvent, PRINCIPAL));

    assertEquals("assessmentSupplierTarget is not applicable for existing eventType: RFI",
        ex.getMessage());
  }

  @Test
  void testValidateUpdateEventAssessment_assSupTgtInvalidForDAA() {
    var updateEvent = new UpdateEvent().assessmentId(1).assessmentSupplierTarget(10);
    var procurementEvent = ProcurementEvent.builder().eventType("DAA").build();

    var ex = assertThrows(ValidationException.class, () -> validationService
        .validateUpdateEventAssessment(updateEvent, procurementEvent, PRINCIPAL));

    assertEquals("assessmentSupplierTarget must be 1 for event type DAA", ex.getMessage());
  }

  @Test
  void testValidationMinMaxValue() {
    var maxValue = BigDecimal.valueOf(100);
    var minValue = BigDecimal.valueOf(107);
    var ex = assertThrows(ValidationException.class,
        () -> validationService.validateMinMaxValue(maxValue, minValue));
    assertEquals("Max Value 100 should greater than or equal to Min value 107", ex.getMessage());
  }


 @Test
  void shouldThrowValidationExceptionWhenTheProjectDurationIsInvalidISO8601Format(){

    QuestionNonOCDSOptions questionNonOCDSOptions= new QuestionNonOCDSOptions();
    questionNonOCDSOptions.setValue("4BAC");

    ValidationException validationException= assertThrows(ValidationException.class,
            () -> validationService.validateProjectDuration(List.of(questionNonOCDSOptions)));
    assertEquals("Project Duration is not in ISO8601 format: '4BAC'",validationException.getMessage());
  }

  @Test
  void shouldThrowValidationExceptionWhenTheProjectDurationIsGreaterThan4years(){

    QuestionNonOCDSOptions questionNonOCDSOptions= new QuestionNonOCDSOptions();
    questionNonOCDSOptions.setValue("P4Y0M1D");

    ValidationException validationException= assertThrows(ValidationException.class,
            () -> validationService.validateProjectDuration(List.of(questionNonOCDSOptions)));
    assertEquals("Project Duration is greater than 4 years",validationException.getMessage());

  }

  @Test
  void shouldNotThrowValidationExceptionWhenTheProjectDurationIsGivenExactlySameDays() {
    QuestionNonOCDSOptions questionNonOCDSOptions = new QuestionNonOCDSOptions();
    questionNonOCDSOptions.setValue("P3Y11M31D");
    try {
      validationService.validateProjectDuration(List.of(questionNonOCDSOptions));
    } catch (Exception e) {
      fail("should not through any exception");
    }
  }

  @Test
  void shouldNotThrowValidationExceptionWhenTheProjectDurationIsGivenNull() {
    QuestionNonOCDSOptions questionNonOCDSOptions = new QuestionNonOCDSOptions();
    questionNonOCDSOptions.setValue(null);
    try {
      validationService.validateProjectDuration(List.of(questionNonOCDSOptions));
    } catch (Exception e) {
      fail("should not through any exception");
    }
  }

}
