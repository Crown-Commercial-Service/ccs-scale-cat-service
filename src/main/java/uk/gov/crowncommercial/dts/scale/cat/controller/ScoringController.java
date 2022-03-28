package uk.gov.crowncommercial.dts.scale.cat.controller;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.ScoreAndCommentNonOCDS;
import uk.gov.crowncommercial.dts.scale.cat.service.SupplierService;

@RestController
@RequestMapping(path = "/tenders/projects/{procId}/events/{eventId}/scores",
    produces = APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Slf4j
@Validated
public class ScoringController extends AbstractRestController {

  private final SupplierService supplierService;

  @PutMapping
  public ResponseEntity<String> updateScoreAndComment(@PathVariable("procId") final Integer procId,
      @PathVariable("eventId") final String eventId,
      @RequestBody final List<ScoreAndCommentNonOCDS> scoresAndComments,
      final JwtAuthenticationToken authentication) {
    var principal = getPrincipalFromJwt(authentication);
    var conclaveOrgId = getCiiOrgIdFromJwt(authentication);
    log.info("update score and comments to suppliers invoked on behalf of principal: {}", principal,
        conclaveOrgId);
    return ResponseEntity.ok(supplierService.updateSupplierScoreAndComment(principal, procId,
        eventId, scoresAndComments));
  }
}
