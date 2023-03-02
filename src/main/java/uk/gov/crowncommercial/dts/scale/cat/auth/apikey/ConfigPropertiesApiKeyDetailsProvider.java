package uk.gov.crowncommercial.dts.scale.cat.auth.apikey;

import java.util.Objects;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.core.authority.AuthorityUtils;
import lombok.RequiredArgsConstructor;
import uk.gov.crowncommercial.dts.scale.cat.config.ApiKeyConfig;

/**
 * Implementation of the ApiKeyDetailsProvider that retrieves details from the Spring Boot
 * application config properties.
 */
@RequiredArgsConstructor
public class ConfigPropertiesApiKeyDetailsProvider implements ApiKeyDetailsProvider {

  private final ApiKeyConfig apiKeyConfig;

  @Override
  public Optional<ApiKeyDetails> findDetailsByKey(String key) {

    // catch no key configured (avoid issues with null/empty/blank keys)
    if (StringUtils.isBlank(apiKeyConfig.getKey())) {
      return Optional.empty();
    }

    // catch no key passed in (avoid issues with null/empty/blank keys)
    if (StringUtils.isBlank(key)) {
      return Optional.empty();
    }

    // check for match
    if (!Objects.equals(apiKeyConfig.getKey(), key)) {
      return Optional.empty();
    }

    // build the details
    return Optional.of(ApiKeyDetails.builder().key(apiKeyConfig.getKey())
        .authorities(
            AuthorityUtils.commaSeparatedStringToAuthorityList(apiKeyConfig.getAuthorities()))
        .build());
  }
}
