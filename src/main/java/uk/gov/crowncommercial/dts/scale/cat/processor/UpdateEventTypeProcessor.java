package uk.gov.crowncommercial.dts.scale.cat.processor;

import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.UpdateEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.Rfx;

/**
 * Processor for updating an event type.
 *
 */
@Slf4j
public class UpdateEventTypeProcessor extends UpdateEventProcessor {

  public UpdateEventTypeProcessor(UpdateEventProcessor nextProcessor) {
    super(nextProcessor);
  }

  @Override
  public void processItem(UpdateEvent updateEvent, Rfx rfx, ProcurementEvent dbEvent,
      String principal) {

    // TODO: Need clarification on the rules here - are there any restrictions on eventType state
    // changes? - see SCC-655/SCC-656
    log.debug("Update Event Type");
    if (updateEvent.getEventType() != null) {
      dbEvent.setEventType(updateEvent.getEventType().getValue());
      dbEvent.setUpdatedAt(Instant.now());
      dbEvent.setUpdatedBy(principal);
    }
  }

}
