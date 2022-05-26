package uk.gov.crowncommercial.dts.scale.cat.controller;

import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import uk.gov.crowncommercial.dts.scale.cat.config.Constants;
import uk.gov.crowncommercial.dts.scale.cat.exception.MalformedJwtException;

import java.util.Optional;

import static uk.gov.crowncommercial.dts.scale.cat.config.Constants.JWT_CLAIM_SUBJECT;

/**
 * Base class for shared state and behaviour
 */
public abstract class AbstractRestController {

  protected static final String SUPPLIER_DATA = "Supplier_Dimension_Data_%s_%s.csv";
  protected String getPrincipalFromJwt(final JwtAuthenticationToken authentication) {
    return Optional.ofNullable(authentication.getTokenAttributes().get(JWT_CLAIM_SUBJECT))
        .map(Object::toString).orElseThrow(() -> new MalformedJwtException(
            "JWT did not contain mandatory '" + Constants.JWT_CLAIM_SUBJECT + "' claim"));
  }

  protected String getCiiOrgIdFromJwt(final JwtAuthenticationToken authentication) {
    return Optional
        .ofNullable(authentication.getTokenAttributes().get(Constants.JWT_CLAIM_CII_ORG_ID))
        .map(Object::toString).orElseThrow(() -> new MalformedJwtException(
            "JWT did not contain '" + Constants.JWT_CLAIM_CII_ORG_ID + "' claim"));
  }
}
