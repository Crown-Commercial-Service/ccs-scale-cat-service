package uk.gov.crowncommercial.dts.scale.cat.controller;

import static uk.gov.crowncommercial.dts.scale.cat.config.Constants.JWT_CLAIM_SUBJECT;
import java.util.Optional;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import uk.gov.crowncommercial.dts.scale.cat.config.Constants;
import uk.gov.crowncommercial.dts.scale.cat.exception.MalformedJwtException;

/**
 * Base class for shared state and behaviour
 */
public abstract class AbstractRestController {

  protected String getPrincipalFromJwt(JwtAuthenticationToken authentication) {
    return Optional.ofNullable(authentication.getTokenAttributes().get(JWT_CLAIM_SUBJECT))
        .map(Object::toString).orElseThrow(() -> new MalformedJwtException(
            "JWT did not contain mandatory '" + Constants.JWT_CLAIM_SUBJECT + "' claim"));
  }

}
