package uk.gov.crowncommercial.dts.scale.cat.controller;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.config.Constants;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.CreateEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.EventDetail;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.EventStatus;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.ProcurementEventName;
import uk.gov.crowncommercial.dts.scale.cat.service.ProcurementEventService;

/**
 *
 */
@RestController
@RequestMapping(path = "/tenders/projects/{procID}/events", produces = APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Slf4j
public class EventsController extends AbstractRestController {

  private final ProcurementEventService procurementEventService;

  @PostMapping
  public EventStatus createProcurementEvent(@PathVariable("procID") Integer procId,
      @RequestBody CreateEvent createEvent, JwtAuthenticationToken authentication) {

    var principal = getPrincipalFromJwt(authentication);
    log.info("createProcuremenEvent invoked on behalf of principal: {}", principal);

    return procurementEventService.createEvent(procId, createEvent, null, principal);
  }

  @PutMapping("/{eventID}/name")
  public String updateProcurementEventName(@PathVariable("procID") Integer procId,
      @PathVariable("eventID") String eventId, @RequestBody ProcurementEventName eventName,
      JwtAuthenticationToken authentication) {

    var principal = getPrincipalFromJwt(authentication);
    log.info("updateProcurementEventName invoked on behalf of principal: {}", principal);

    procurementEventService.updateProcurementEventName(procId, eventId, eventName.getName(),
        principal);

    return Constants.OK;
  }

  @GetMapping("/{eventID}")
  public EventDetail getEvent(@PathVariable("procID") Integer procId,
      @PathVariable("eventID") String eventId, JwtAuthenticationToken authentication) {

    var principal = getPrincipalFromJwt(authentication);
    log.info("getEvent invoked on behalf of principal: {}", principal);

    return procurementEventService.getEvent(procId, eventId, principal);
  }
}
