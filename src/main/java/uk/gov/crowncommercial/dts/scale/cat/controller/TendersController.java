package uk.gov.crowncommercial.dts.scale.cat.controller;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import java.util.Arrays;
import java.util.Collection;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.DefineEventType;

/**
 * Tenders ('Base Data' tag in YAML)
 */
@RestController
@RequestMapping(path = "/tenders", produces = APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Slf4j
public class TendersController extends AbstractRestController {

  @GetMapping("/event-types")
  public Collection<DefineEventType> listProcurementEventTypes(
      JwtAuthenticationToken authentication) {

    log.info("listProcurementEventTypes invoked on behalf of principal: {}",
        getPrincipalFromJwt(authentication));

    return Arrays.asList(DefineEventType.values());
  }

}
