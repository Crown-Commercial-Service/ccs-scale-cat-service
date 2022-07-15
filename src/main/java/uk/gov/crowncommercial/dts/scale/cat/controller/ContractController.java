package uk.gov.crowncommercial.dts.scale.cat.controller;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.model.StringValueResponse;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.Contract;
import uk.gov.crowncommercial.dts.scale.cat.service.ProcurementEventService;

/**
 *
 */
@RestController
@RequestMapping(path = "/tenders/projects/{procID}/events/{eventID}/contracts",
    produces = APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Slf4j
@Validated
public class ContractController extends AbstractRestController {

  private final ProcurementEventService procurementEventService;

  @PostMapping
  public StringValueResponse signProcurement(@PathVariable("procID") final Integer procId,
      @PathVariable("eventID") final String eventId, @RequestBody final Contract contract,
      final JwtAuthenticationToken authentication) {
    var principal = getPrincipalFromJwt(authentication);
    log.info("signProcurement invoked on behalf of principal: {}", principal);
    procurementEventService.signProcurement(procId, eventId, contract, principal);
    return new StringValueResponse("OK");
  }

  @GetMapping
  public ResponseEntity<Contract> getContract(@PathVariable("procID") final Integer procId,
      @PathVariable("eventID") final String eventId,
      final JwtAuthenticationToken authentication) {
    var principal = getPrincipalFromJwt(authentication);
    log.info("signProcurement invoked on behalf of principal: {}", principal);
    return ResponseEntity.ok(procurementEventService.getContract(procId, eventId, principal));
  }
}
