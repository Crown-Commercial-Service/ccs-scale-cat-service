package uk.gov.crowncommercial.dts.scale.cat.controller;

import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.EventSummary;
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
    log.info("createProcuremenProject invoked on behalf of principal: {}", principal);

    return procurementEventService.createFromTender(procId, tender, principal);
  }

}
