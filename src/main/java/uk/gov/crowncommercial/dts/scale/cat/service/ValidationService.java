package uk.gov.crowncommercial.dts.scale.cat.service;

import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import uk.gov.crowncommercial.dts.scale.cat.exception.ResourceNotFoundException;
import uk.gov.crowncommercial.dts.scale.cat.model.OCID;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;

/**
 * Captures Project and Event input validation functionality
 */
@Service
@RequiredArgsConstructor
public class ValidationService {

  private final RetryableTendersDBDelegate retryableTendersDBDelegate;

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
  public OCID validateEventId(String eventId) {
    try {
      return OCID.fromString(eventId);
    } catch (Exception e) {
      throw new IllegalArgumentException(
          "Event ID '" + eventId + "' is not in the expected format");
    }
  }

}
