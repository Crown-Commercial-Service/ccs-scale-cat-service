package uk.gov.crowncommercial.dts.scale.cat.service;

import static uk.gov.crowncommercial.dts.scale.cat.config.Constants.ASSESSMENT_EVENT_TYPES;
import static uk.gov.crowncommercial.dts.scale.cat.config.Constants.NOT_ALLOWED_EVENTS_AFTER_AWARD;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;
import javax.validation.ValidationException;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import uk.gov.crowncommercial.dts.scale.cat.exception.ResourceNotFoundException;
import uk.gov.crowncommercial.dts.scale.cat.model.OCID;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.DefineEventType;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.PublishDates;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.UpdateEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.ViewEventType;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.ExportRfxResponse;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;
import uk.gov.crowncommercial.dts.scale.cat.service.ca.AssessmentService;

/**
 * Captures Project and Event input validation functionality
 */
@Service
@RequiredArgsConstructor
public class ValidationService {

  private final RetryableTendersDBDelegate retryableTendersDBDelegate;
  private final AssessmentService assessmentService;
  private final Clock clock;
  private static final Integer AWARD_STATUS = 500;
  private static final Integer ABANDONED_STATUS = 1500;
  private static final String MONETARY_QUESTION_TYPE = "Monetary";

  /**
   * Validate the project and event IDs and return the {@link ProcurementEvent} entity
   *
   * @param projectId
   * @param eventId
   * @return procurement event entity
   */
  public ProcurementEvent validateProjectAndEventIds(final Integer projectId,
      final String eventId) {
    var eventOCID = validateEventId(eventId);

    // Get event from tenders DB to obtain Jaggaer project id
    var event = retryableTendersDBDelegate
        .findProcurementEventByIdAndOcdsAuthorityNameAndOcidPrefix(
            Integer.valueOf(eventOCID.getInternalId()), eventOCID.getAuthority(),
            eventOCID.getPublisherPrefix())
        .orElseThrow(() -> new ResourceNotFoundException("Event '" + eventId + "' not found"));

    // Validate projectId is correct
    if (!event.getProject().getId().equals(projectId)) {
      throw new ResourceNotFoundException(
          "Project '" + projectId + "' is not valid for event '" + eventId + "'");
    }

    return event;
  }

  /**
   * Validate the eventId is a valid {@link OCID}
   *
   * @param eventId
   * @return an OCID
   */
  public OCID validateEventId(final String eventId) {
    try {
      return OCID.fromString(eventId);
    } catch (Exception e) {
      throw new IllegalArgumentException(
          "Event ID '" + eventId + "' is not in the expected format");
    }
  }

  /**
   * Validates the publish event dates (Note: startDate currently ignored so not validated)
   *
   * @param publishDates
   * @throws IllegalArgumentException if the endDate is not in the future
   */
  public void validatePublishDates(final PublishDates publishDates) {

    var now = OffsetDateTime.now(clock);

    if (!publishDates.getEndDate().isAfter(now)) {
      throw new IllegalArgumentException("endDate must be in the future");
    }

  }

  /**
   * Validates the Capabilty Assessment related properties of {@link UpdateEvent}, in combination
   * with the supplied {@link DefineEventType}
   *
   * @param updateEvent
   * @param principal
   * @throws ValidationException if provided values are invalid or represent an invalid update
   *         combination
   */
  public void validateUpdateEventAssessment(final UpdateEvent updateEvent,
      final ProcurementEvent existingEvent, final String principal) {

    // Post MVP - user may have created an assessment before commencing CaT journey so validate
    if (updateEvent.getAssessmentId() != null) {

      if (updateEvent.getEventType() != null
          && !ASSESSMENT_EVENT_TYPES.contains(updateEvent.getEventType())) {
        throw new ValidationException(
            "assessmentId is invalid for eventType: " + updateEvent.getEventType());
      }

      // Verify the assessment with that ID already exists
      assessmentService.getAssessment(updateEvent.getAssessmentId(), Optional.empty());
    }

    if (updateEvent.getAssessmentSupplierTarget() != null) {

      // SCAT-3504 AC5. Check existing event type is valid for setting assessmentSupplierTarget
      if (!existingEvent.isAssessment()) {
        throw new ValidationException(
            "assessmentSupplierTarget is not applicable for existing eventType: "
                + existingEvent.getEventType());
      }

      if (updateEvent.getAssessmentId() == null) {
        throw new ValidationException(
            "assessmentId must be provided with assessmentSupplierTarget");
      }

      // SCAT-3504 AC4
      if (Objects.equals(DefineEventType.DAA.name(), existingEvent.getEventType())
          && updateEvent.getAssessmentSupplierTarget() > 1) {
        throw new ValidationException("assessmentSupplierTarget must be 1 for event type DAA");
      }
    }

  }

  /**
   * Validates the end date
   *
   * @param endDate
   * @throws IllegalArgumentException if the endDate is not in the future
   */
  public void validateEndDate(final OffsetDateTime endDate) {

    var now = OffsetDateTime.now(clock);

    if (!endDate.isAfter(now)) {
      throw new IllegalArgumentException("endDate must be in the future");
    }
  }

  public void validateEventTypeBeforeUpdate(final ExportRfxResponse exportRfxResponse,
      final DefineEventType eventType) {
    // cannot move to a TBD event from any other event
    if (ViewEventType.TBD.name().equals(eventType.getValue())) {
      throw new IllegalArgumentException(
          "Cannot update an existing event type of '" + eventType.getValue() + "'");
    }
    // If event status is AWARD, you cannot do (DA, FC, EOI, RFI)
    if (exportRfxResponse.getRfxSetting().getStatusCode().equals(AWARD_STATUS)
        && NOT_ALLOWED_EVENTS_AFTER_AWARD.contains(eventType)) {
      throw new IllegalArgumentException(
          "Cannot update an existing event type of '" + eventType.getValue() + "'");
    }
  }

  public boolean isEventAbandoned(final ExportRfxResponse exportRfxResponse,
      final DefineEventType eventType) {
    return exportRfxResponse.getRfxSetting().getStatusCode().equals(ABANDONED_STATUS);
  }

  public void validateMinMaxValue(BigDecimal maxValue, BigDecimal minValue, String questionType) {
    if (ObjectUtils.allNotNull(maxValue, minValue, questionType)
        && questionType.contentEquals(MONETARY_QUESTION_TYPE) && maxValue.compareTo(minValue) < 0) {
      throw new ValidationException(
          String.format("Max Value %s should greater than Min value %s", maxValue, minValue));
    }
  }

}
