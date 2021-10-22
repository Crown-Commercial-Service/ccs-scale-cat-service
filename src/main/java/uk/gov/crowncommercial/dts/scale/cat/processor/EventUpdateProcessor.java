package uk.gov.crowncommercial.dts.scale.cat.processor;

import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.UpdateEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.Rfx;

@Slf4j
public abstract class EventUpdateProcessor {

  protected EventUpdateProcessor nextProcessor;

  EventUpdateProcessor(EventUpdateProcessor nextProcessor) {
    if (nextProcessor != null) {
      this.nextProcessor = nextProcessor;
    } else {
      log.debug("Processing complete");
    }
  }

  public void process(UpdateEvent updateEvent, Rfx rfx, ProcurementEvent dbEvent,
      String principal) {

    this.processItem(updateEvent, rfx, dbEvent, principal);

    if (nextProcessor != null) {
      nextProcessor.process(updateEvent, rfx, dbEvent, principal);
    }

  }

  abstract void processItem(UpdateEvent updateEvent, Rfx rfx, ProcurementEvent dbEvent,
      String principal);
}
