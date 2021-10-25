package uk.gov.crowncommercial.dts.scale.cat.processor;

import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.UpdateEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.Rfx;

/**
 * Processor for updating an event name.
 *
 */
@Slf4j
public class UpdateEventNameProcessor extends UpdateEventProcessor {

  public UpdateEventNameProcessor(UpdateEventProcessor nextProcessor) {
    super(nextProcessor);
  }

  @Override
  void processItem(UpdateEvent updateEvent, Rfx rfx, ProcurementEvent dbEvent, String principal) {

    log.debug("Update Event Name");
    if (!updateEvent.getName().isEmpty()) {
      rfx.getRfxSetting().setShortDescription(updateEvent.getName());
    }
  }

}
