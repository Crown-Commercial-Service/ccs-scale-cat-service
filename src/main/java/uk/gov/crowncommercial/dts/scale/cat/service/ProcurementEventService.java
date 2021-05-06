package uk.gov.crowncommercial.dts.scale.cat.service;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.EventSummary;

/**
 *
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProcurementEventService {

  private final JaggaerAPIConfig jaggaerAPIConfig;
  private final WebClient jaggaerWebClient;

  public EventSummary createFromAgreementDetails(Integer projectId, String principal) {
    EventSummary eventSummary = new EventSummary();
    eventSummary.setEventID("ocds-b5fd17-1");
    return eventSummary;
  }

}
