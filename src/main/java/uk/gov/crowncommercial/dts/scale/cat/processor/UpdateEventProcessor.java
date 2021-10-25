package uk.gov.crowncommercial.dts.scale.cat.processor;

import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.UpdateEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.Rfx;

/**
 * Superclass for UpdateEvent processors, implementing the Chain of Responsibility design pattern.
 * 
 * UpdateEvent only contains a small number of properties that are allowed to be updated on an Event
 * at present. However, if this grows in the future then this approach should prevent any
 * proliferation of complicated if.. then logic in a single update method.
 *
 */
@Slf4j
public abstract class UpdateEventProcessor {

  protected UpdateEventProcessor nextProcessor;

  UpdateEventProcessor(UpdateEventProcessor nextProcessor) {
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
