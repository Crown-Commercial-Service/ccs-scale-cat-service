package uk.gov.crowncommercial.dts.scale.cat.processor;

import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.UpdateEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.Rfx;

@Slf4j
public class UpdateEventTypeProcessor extends EventUpdateProcessor {

  public UpdateEventTypeProcessor(EventUpdateProcessor nextProcessor) {
    super(nextProcessor);
  }

  @Override
  public void processItem(UpdateEvent updateEvent, Rfx rfx, ProcurementEvent dbEvent,
      String principal) {

    log.debug("Update Event Type");
    if (updateEvent.getEventType() != null) {
      dbEvent.setEventType(updateEvent.getEventType().getValue());
      dbEvent.setUpdatedAt(Instant.now());
      dbEvent.setUpdatedBy(principal);
    }
  }

}
