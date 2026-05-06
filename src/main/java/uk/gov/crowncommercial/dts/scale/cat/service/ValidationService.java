package uk.gov.crowncommercial.dts.scale.cat.service;

import static uk.gov.crowncommercial.dts.scale.cat.config.Constants.ASSESSMENT_EVENT_TYPES;
import static uk.gov.crowncommercial.dts.scale.cat.config.Constants.NOT_ALLOWED_EVENTS_AFTER_AWARD;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import jakarta.validation.ValidationException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import uk.gov.crowncommercial.dts.scale.cat.exception.ResourceNotFoundException;
import uk.gov.crowncommercial.dts.scale.cat.model.OCID;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.DataTemplate;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.Requirement;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.RequirementGroup;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.TemplateCriteria;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.Requirement.Option;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.*;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.ExportRfxResponse;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;
import uk.gov.crowncommercial.dts.scale.cat.service.ca.AssessmentService;

/**
 * Captures Project and Event input validation functionality
 */

@Service
@RequiredArgsConstructor
@Slf4j
public class ValidationService {

  private static final String LOG_TAG = "12322912 - ";

  private static final Integer AWARD_STATUS = 500;
  private static final Integer ABANDONED_STATUS = 1500;

  private static final Period FOUR_YEAR_PERIOD = Period.parse("P4Y");

  private static final String COP_GROUP_ID = "Group 1";
  private static final String AWARD_CRITERIA_GROUP_ID = "Group 2";
  private static final String ASSESSMENT_CRITERIA_CRITERION_ID = "Criterion 2";

  private final RetryableTendersDBDelegate retryableTendersDBDelegate;
  private final AssessmentService assessmentService;
  private final Clock clock;

  /**
   * Validate the project and event IDs and return the {@link ProcurementEvent} entity
   *
   * @param projectId
   * @param eventId
   * @return procurement event entity
   */
  public ProcurementEvent validateProjectAndEventIds(final Integer projectId, final String eventId, final Integer stageNumber) {
    // Get event from tenders DB to obtain Jaggaer project id

    var eventOCID = validateEventId(eventId);

    ProcurementEvent procurementEventForStage = null;
    DataTemplate procurementStageEventDataTemplate = null;

    if (null != stageNumber && stageNumber > 0) {
        // grab the stage-specific data
        // (we will then merge it into the response to return, below)
        procurementEventForStage = retryableTendersDBDelegate
                .findProcurementEventByIdAndStageNumberAndOcdsAuthorityNameAndOcidPrefix(
                    Integer.valueOf(eventOCID.getInternalId()), stageNumber, eventOCID.getAuthority(), eventOCID.getPublisherPrefix())
                .orElse(null);

        procurementStageEventDataTemplate = null == procurementEventForStage ? null : procurementEventForStage.getProcurementTemplatePayload();

    }

    ProcurementEvent event = retryableTendersDBDelegate
                .findProcurementEventByIdAndOcdsAuthorityNameAndOcidPrefix(
                    Integer.valueOf(eventOCID.getInternalId()), eventOCID.getAuthority(), eventOCID.getPublisherPrefix())
                .orElseThrow(() -> new ResourceNotFoundException("Event '" + eventId + "' not found"));

    final DataTemplate dataTemplate = event.getProcurementTemplatePayload();

    if (null != dataTemplate && null != dataTemplate.getCriteria()) {

        boolean updated = false;

        for (final TemplateCriteria criteria: dataTemplate.getCriteria()) {

            // we are only interested in 'Criterion 2'
            if (null == criteria || null == criteria.getRequirementGroups() || !ASSESSMENT_CRITERIA_CRITERION_ID.equals(criteria.getId())) {
                continue;
            }

            for (final RequirementGroup requirementGroup: criteria.getRequirementGroups()) {

                if (null == requirementGroup || null == requirementGroup.getOcds() || null == requirementGroup.getOcds().getRequirements()) {
                    continue;
                }

                // we are only interested in CoP and Award Criteria
                if (!COP_GROUP_ID.equals(requirementGroup.getOcds().getId()) &&
                    !AWARD_CRITERIA_GROUP_ID.equals(requirementGroup.getOcds().getId())) {
                    continue;
                }

                if (null != procurementEventForStage) {
                    // we are in multi-stage, so replace the current data
                    // with the data associated with the given stage

                    Set<Requirement> stageRequirements = extractStageRequirements(requirementGroup.getOcds().getId(), procurementStageEventDataTemplate);

                    if (null != stageRequirements) {
                        requirementGroup.getOcds().setRequirements(stageRequirements);
                        updated = true;
                    }

                    continue;
                }

                if (null != stageNumber && stageNumber > 0) {
                    // we are in multi-stage. but we don't have any stage-specific data,
                    // so clear any stored answers to prevent pre-population of fields

                    for (final Requirement requirement: requirementGroup.getOcds().getRequirements()) {

                        if (null == requirement || null == requirement.getNonOCDS() || null == requirement.getNonOCDS().getOptions()) {
                            continue;
                        }

                        if ("Text".equalsIgnoreCase(requirement.getNonOCDS().getQuestionType()) ||
                            "Value".equalsIgnoreCase(requirement.getNonOCDS().getQuestionType()) ||
                            "Integer".equalsIgnoreCase(requirement.getNonOCDS().getQuestionType())) {

                            for (final Option option: requirement.getNonOCDS().getOptions()) {
                                option.setValue("");
                                option.setSelect(false);
                                updated = true;
                            }

                            continue;
                        }

                        if ("SingleSelect".equalsIgnoreCase(requirement.getNonOCDS().getQuestionType()) ||
                            "MultiSelect".equalsIgnoreCase(requirement.getNonOCDS().getQuestionType())) {

                            for (final Option option: requirement.getNonOCDS().getOptions()) {
                                option.setSelect(false);
                                updated = true;
                            }

                            continue;
                        }
                    }
                }
            }
        }

        if (updated) {
            event.setProcurementTemplatePayload(dataTemplate);
        }
    }

    log.debug(LOG_TAG + "Retrieved event from tender db, event: {}", event);

    // Validate projectId is correct
    if (!event.getProject().getId().equals(projectId)) {
      log.error("Project '" + projectId + "' is not valid for event '" + eventId + "'");
      throw new ResourceNotFoundException("Project '" + projectId + "' is not valid for event '" + eventId + "'");
    }

    return event;
  }

  private Set<Requirement> extractStageRequirements(final String groupId, DataTemplate procurementStageEventDataTemplate) {
      if (null != procurementStageEventDataTemplate && null != procurementStageEventDataTemplate.getCriteria()) {
          for (final TemplateCriteria criteria: procurementStageEventDataTemplate.getCriteria()) {
              // we are only interested in 'Criterion 2'
              if (null == criteria || null == criteria.getRequirementGroups() || !ASSESSMENT_CRITERIA_CRITERION_ID.equals(criteria.getId())) {
                  continue;
              }

              for (final RequirementGroup requirementGroup: criteria.getRequirementGroups()) {
                  if (null == requirementGroup || null == requirementGroup.getOcds() || null == requirementGroup.getOcds().getRequirements()) {
                      continue;
                  }

                  // we are only interested in our given group
                  if (groupId.equals(requirementGroup.getOcds().getId())) {
                      return requirementGroup.getOcds().getRequirements();
                  }
              }
          }
      }

      return null;
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
      assessmentService.getAssessment(updateEvent.getAssessmentId(), false, Optional.empty());
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
      final String eventType) {
    // cannot move to a TBD event from any other event
    if (ViewEventType.TBD.name().equals(eventType)) {
      throw new IllegalArgumentException(
          "Cannot update an existing event type of '" + eventType + "'");
    }
    // If event status is AWARD, you cannot do (DA, FC, EOI, RFI)
    if (exportRfxResponse.getRfxSetting().getStatusCode().equals(AWARD_STATUS)
        && NOT_ALLOWED_EVENTS_AFTER_AWARD.contains(eventType)) {
      throw new IllegalArgumentException(
          "Cannot update an existing event type of '" + eventType + "'");
    }
  }

  @Deprecated
  public void validateProjectDuration(final List<QuestionNonOCDSOptions> optionList) {

    if (!CollectionUtils.isEmpty(optionList) && optionList.size() == 1) {
      var projectDurationOptionValue = optionList.get(0);

      if (null != projectDurationOptionValue
          && !ObjectUtils.isEmpty(projectDurationOptionValue.getValue())) {
        try {
          var projectDuration = Period.parse(projectDurationOptionValue.getValue());
          // Current date is giving an issue with days so made constant date for all
          var now = LocalDate.of(1970, 1, 1);
          if (now.plus(projectDuration).isAfter(now.plus(FOUR_YEAR_PERIOD))) {
            throw new ValidationException(
                String.format("Project Duration is greater than 4 years"));
          }

        } catch (DateTimeParseException dateTimeParseException) {
          throw new ValidationException(
              String.format("Project Duration is not in ISO8601 format: '%s'",
                  projectDurationOptionValue.getValue()));
        }
      }
    }
  }

  public boolean isEventAbandoned(final ExportRfxResponse exportRfxResponse,
      final DefineEventType eventType) {
    return exportRfxResponse.getRfxSetting().getStatusCode().equals(ABANDONED_STATUS);
  }

  public void validateMinMaxValue(final BigDecimal maxValue, final BigDecimal minValue) {
    if (ObjectUtils.allNotNull(maxValue, minValue) && maxValue.compareTo(minValue) < 0) {
      throw new ValidationException(String
          .format("Max Value %s should greater than or equal to Min value %s", maxValue, minValue));
    }
  }

}
