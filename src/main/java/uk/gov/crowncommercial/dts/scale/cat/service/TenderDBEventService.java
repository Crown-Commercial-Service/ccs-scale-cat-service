package uk.gov.crowncommercial.dts.scale.cat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.EventSummary;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.ReleaseTag;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.UpdateEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.ViewEventType;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;
import uk.gov.crowncommercial.dts.scale.cat.service.ca.AssessmentService;
import uk.gov.crowncommercial.dts.scale.cat.utils.TendersAPIModelUtils;

import java.time.Instant;
import java.util.Optional;

import static uk.gov.crowncommercial.dts.scale.cat.config.Constants.ASSESSMENT_EVENT_TYPES;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenderDBEventService {
    private static final ReleaseTag EVENT_STAGE = ReleaseTag.TENDER;

    private final ValidationService validationService;
    private final AssessmentService assessmentService;
    private final RetryableTendersDBDelegate retryableTendersDBDelegate;
    private final TendersAPIModelUtils tendersAPIModelUtils;

    public EventSummary updateDBProcurementEvent(final ProcurementEvent event,
                                               final UpdateEvent updateEvent, final String principal) {

        log.debug("Update Event {}", updateEvent);


        if (updateEvent.getEventType() != null) {
            // validate different rules before update
            validateEventTypeBeforeUpdate(updateEvent.getEventType().getValue());
        }
        validationService.validateUpdateEventAssessment(updateEvent, event, principal);

        var updateDB = false;
        var createAssessment = false;
        Integer returnAssessmentId = null;

        // Update event name
        if (StringUtils.hasText(updateEvent.getName())) {
            event.setEventName(updateEvent.getName());
            updateDB = true;
        }

        // Update event type
        if (updateEvent.getEventType() != null) {
            if (!ViewEventType.TBD.name().equals(event.getEventType())) {
                throw new IllegalArgumentException(
                        "Cannot update an existing event type of '" + event.getEventType() + "'");
            }

            if (ASSESSMENT_EVENT_TYPES.contains(updateEvent.getEventType())
                    && updateEvent.getAssessmentId() == null && event.getAssessmentId() == null) {
                createAssessment = true;
            }

            event.setEventType(updateEvent.getEventType().getValue());
            updateDB = true;
        }

        // Valid to supply either for an existing event
        if (updateEvent.getAssessmentId() != null
                || updateEvent.getAssessmentSupplierTarget() != null) {
            event.setAssessmentSupplierTarget(updateEvent.getAssessmentSupplierTarget());
            event.setAssessmentId(updateEvent.getAssessmentId());
            updateDB = true;
        }

        // Create a new empty assessment
        if (createAssessment) {
            returnAssessmentId = assessmentService.createEmptyAssessment(event.getProject().getCaNumber(),
                    event.getProject().getLotNumber(), updateEvent.getEventType(), principal);
        } else if (updateEvent.getAssessmentId() != null) {
            // Return the existing (validated) assessmentId
            returnAssessmentId = updateEvent.getAssessmentId();
        }
        // Save to Tenders DB
        if (updateDB) {
            event.setUpdatedAt(Instant.now());
            event.setUpdatedBy(principal);
            event.setAssessmentId(returnAssessmentId);
            retryableTendersDBDelegate.save(event);
        }

        return tendersAPIModelUtils.buildEventSummary(event.getEventID(), event.getEventName(),
                Optional.ofNullable(event.getExternalReferenceId()),
                ViewEventType.fromValue(event.getEventType()), null,
                EVENT_STAGE, Optional.ofNullable(returnAssessmentId));
    }

    public void validateEventTypeBeforeUpdate(final String eventType) {
        if (ViewEventType.TBD.name().equals(eventType)) {
            throw new IllegalArgumentException(
                    "Cannot update an existing event type of '" + eventType + "'");
        }
    }
}
