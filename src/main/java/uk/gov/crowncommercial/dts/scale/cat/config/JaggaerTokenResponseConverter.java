package uk.gov.crowncommercial.dts.scale.cat.config;

import java.util.Map;
import java.util.Optional;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Converts from Jaggaer's non-standard token response to {@link OAuth2AccessTokenResponse}
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JaggaerTokenResponseConverter
    implements Converter<Map<String, String>, OAuth2AccessTokenResponse> {

  static final String JAGGAER_TOKEN_PROPERTY = "token";
  static final String JAGGAER_EXPIRE_IN_MILLIS_PROPERTY = "expire_in";

  private final JaggaerAPIConfig jaggaerAPIConfig;

  @Override
  public OAuth2AccessTokenResponse convert(Map<String, String> tokenResponse) {

    var tokenExpirySeconds = Optional.ofNullable(jaggaerAPIConfig.getTokenExpirySeconds())
        .orElse(Long.parseLong(tokenResponse.get(JAGGAER_EXPIRE_IN_MILLIS_PROPERTY)) / 1000);
    log.debug("Jaggaer token expiry set to [{}] seconds", tokenExpirySeconds);

    return OAuth2AccessTokenResponse.withToken(tokenResponse.get(JAGGAER_TOKEN_PROPERTY))
        .tokenType(OAuth2AccessToken.TokenType.BEARER).expiresIn(tokenExpirySeconds).build();
  }

}
