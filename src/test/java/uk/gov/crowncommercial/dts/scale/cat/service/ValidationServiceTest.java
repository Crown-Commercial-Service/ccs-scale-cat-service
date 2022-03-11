package uk.gov.crowncommercial.dts.scale.cat.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import javax.validation.ValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gov.crowncommercial.dts.scale.cat.config.ApplicationConfig;
import uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.exception.ResourceNotFoundException;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementProject;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.DefineEventType;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.PublishDates;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.UpdateEvent;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;
import uk.gov.crowncommercial.dts.scale.cat.service.ca.AssessmentService;

@SpringBootTest(classes = {ValidationService.class, ApplicationConfig.class},
    webEnvironment = WebEnvironment.NONE)
@EnableConfigurationProperties(JaggaerAPIConfig.class)
class ValidationServiceTest {

  private static final Integer PROC_PROJECT_ID = 1;
  private static final String PROC_EVENT_ID = "ocds-b5fd17-2";
  private static final String PROC_EVENT_INTERNAL_ID = "2";
  private static final String PROC_EVENT_AUTHORITY = "ocds";
  private static final String PROC_EVENT_PREFIX = "b5fd17";
  private static final String PRINCIPAL = "jsmith@ccs.org.uk";

  @Autowired
  ValidationService validationService;

  @MockBean
  RetryableTendersDBDelegate retryableTendersDBDelegate;

  @MockBean
  AssessmentService assessmentService;

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
}
