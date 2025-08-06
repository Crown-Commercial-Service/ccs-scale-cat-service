package uk.gov.crowncommercial.dts.scale.cat.controller;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
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

  /**
   * Extract the principal (email) from a given authentication token
   */
  protected String getPrincipalFromJwt(final JwtAuthenticationToken authentication) {
    return Optional.ofNullable(authentication.getTokenAttributes().get(JWT_CLAIM_SUBJECT))
        .map(Object::toString).orElseThrow(() -> new MalformedJwtException(
            "JWT did not contain mandatory '" + Constants.JWT_CLAIM_SUBJECT + "' claim"));
  }

  /**
   * Extract the user's organisation ID from a given authentication token
   */
  protected String getCiiOrgIdFromJwt(final JwtAuthenticationToken authentication) {
    return Optional
        .ofNullable(authentication.getTokenAttributes().get(Constants.JWT_CLAIM_CII_ORG_ID))
        .map(Object::toString).orElseThrow(() -> new MalformedJwtException(
            "JWT did not contain '" + Constants.JWT_CLAIM_CII_ORG_ID + "' claim"));
  }

  /**
   * Determines from a given authentication token whether or not a user has administrative access to the system
   */
  protected boolean doesTokenAllowAdminAccess(final JwtAuthenticationToken auth) {
      return auth != null && auth.getAuthorities() != null && auth.getAuthorities().contains(new SimpleGrantedAuthority(Constants.ROLES_ADMIN));
  }
}