package uk.gov.crowncommercial.dts.scale.cat.config;

import java.util.Map;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.stereotype.Component;

/**
 * Converts from Jaggaer's non-standard token response to {@link OAuth2AccessTokenResponse}
 */
@Component
public class JaggaerTokenResponseConverter
    implements Converter<Map<String, String>, OAuth2AccessTokenResponse> {

  static final String JAGGAER_TOKEN_PROPERTY = "token";
  static final String JAGGAER_EXPIRE_IN_PROPERTY = "expire_in";

  @Override
  public OAuth2AccessTokenResponse convert(Map<String, String> tokenResponse) {

    return OAuth2AccessTokenResponse.withToken(tokenResponse.get(JAGGAER_TOKEN_PROPERTY))
        .tokenType(OAuth2AccessToken.TokenType.BEARER)
        .expiresIn(Long.parseLong(tokenResponse.get(JAGGAER_EXPIRE_IN_PROPERTY))).build();
  }

}
