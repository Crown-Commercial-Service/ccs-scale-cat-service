package uk.gov.crowncommercial.dts.scale.cat.controller;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import javax.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.Award2AllOf;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.AwardState;
import uk.gov.crowncommercial.dts.scale.cat.service.AwardService;

/**
 * Award Controller which can do award events
 *
 */
@RestController
@RequestMapping(path = "/tenders/projects/{proc-id}/events/{event-id}",
    produces = APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Slf4j
@Validated
public class AwardController extends AbstractRestController {

  private final AwardService awardService;

  @PostMapping("/state/{award-state}/awards")
  public ResponseEntity<String> createAward(@PathVariable("proc-id") final Integer procId,
      @PathVariable("event-id") final String eventId,
      @PathVariable("award-state") final AwardState awardState,
      @Valid @RequestBody final Award2AllOf awardRequest,
      final JwtAuthenticationToken authentication) {
    var principal = getPrincipalFromJwt(authentication);
    var conclaveOrgId = getCiiOrgIdFromJwt(authentication);
    log.info("createOrComplete award process on behalf of principal: {}", principal, conclaveOrgId);
    return ResponseEntity.ok(awardService.createOrUpdateAward(principal, procId, eventId,
        awardState, awardRequest, null));
  }

  @PutMapping("/state/{award-state}/awards/{award-id}")
  public ResponseEntity<String> updateAward(@PathVariable("proc-id") final Integer procId,
      @PathVariable("event-id") final String eventId,
      @PathVariable("award-state") final AwardState awardState,
      @PathVariable("award-id") final Integer awardId,
      @Valid @RequestBody final Award2AllOf awardRequest,
      final JwtAuthenticationToken authentication) {
    var principal = getPrincipalFromJwt(authentication);
    var conclaveOrgId = getCiiOrgIdFromJwt(authentication);
    log.info("updateOrComplete award process invoked on behalf of principal: {}", principal,
        conclaveOrgId);
    return ResponseEntity.ok(awardService.createOrUpdateAward(principal, procId, eventId,
        awardState, awardRequest, awardId));
  }
}
