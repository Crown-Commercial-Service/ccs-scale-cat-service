package uk.gov.crowncommercial.dts.scale.cat.controller;

import org.springframework.http.MediaType;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.config.Constants;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.EventSummary;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.ProcurementEventName;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.Tender;
import uk.gov.crowncommercial.dts.scale.cat.service.ProcurementEventService;

/**
 *
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class EventsController {

  private final ProcurementEventService procurementEventService;

  // @PreAuthorize("isAuthenticated()")
  @PostMapping("/tenders/projects/{procID}/events")
  public EventSummary createProcurementEvent(@PathVariable("procID") Integer procId,
      @RequestBody Tender tender, JwtAuthenticationToken authentication) {

    var principal = authentication.getTokenAttributes().get("sub").toString();
    log.info("createProcuremenEvent invoked on behalf of principal: {}", principal);

    return procurementEventService.createFromTender(procId, tender, principal);
  }

  @PostMapping(value = "/tenders/projects/{procID}/events/{eventID}/name",
      produces = MediaType.APPLICATION_JSON_VALUE)
  public String updateProcurementEventName(@PathVariable("procID") Integer procId,
      @PathVariable("eventID") String eventId, @RequestBody ProcurementEventName eventName,
      JwtAuthenticationToken authentication) {

    var principal = authentication.getTokenAttributes().get("sub").toString();
    log.info("updateProcurementEventName invoked on behalf of principal: {}", principal);

    procurementEventService.updateProcurementEventName(procId, eventId, eventName.getName(),
        principal);

    return Constants.OK;
  }

}
