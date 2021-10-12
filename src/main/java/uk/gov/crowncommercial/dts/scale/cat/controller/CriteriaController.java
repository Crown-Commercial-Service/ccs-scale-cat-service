package uk.gov.crowncommercial.dts.scale.cat.controller;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import java.util.Set;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.EvalCriteria;
import uk.gov.crowncommercial.dts.scale.cat.service.CriteriaService;

/**
 *
 */
@RestController
@RequestMapping(path = "/tenders/projects/{proc-id}/events/{event-id}/criteria",
    produces = APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Slf4j
public class CriteriaController extends AbstractRestController {

  private final CriteriaService criteriaService;

  @GetMapping
  public Set<EvalCriteria> getEventEvaluationCriteria(@PathVariable("procID") final Integer procId,
      @PathVariable("eventID") final String eventId, final JwtAuthenticationToken authentication) {

    final var principal = getPrincipalFromJwt(authentication);
    log.info("getEventEvaluationCriteria invoked on behalf of principal: {}", principal);

    return criteriaService.getEvalCriteria(procId, eventId);
  }

}
