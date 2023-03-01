package uk.gov.crowncommercial.dts.scale.cat.auth.apikey;

import org.apache.commons.lang3.StringUtils;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import lombok.RequiredArgsConstructor;

/**
 * Spring security AuthenticationManager for HTTP header based API Key authentication.
 * 
 * This delegates the actual API Key checking/details retrieval to a provider.
 */
@RequiredArgsConstructor
public class ApiKeyAuthManager implements AuthenticationManager {

  final ApiKeyDetailsProvider apiKeyDetailsProvider;

  @Override
  public Authentication authenticate(Authentication authentication) {

    String principal = (String) authentication.getPrincipal();

    // check key
    if (StringUtils.isBlank(principal)) {
      throw new BadCredentialsException("API Key is missing");
    }

    return apiKeyDetailsProvider.findDetailsByKey(principal).map((details) -> {
      return new ApiKeyAuthToken(details.getKey(), details.getAuthorities());
    }).orElseThrow(() -> new BadCredentialsException("API Key not found"));
  }
}
