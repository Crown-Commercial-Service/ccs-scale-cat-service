package uk.gov.crowncommercial.dts.scale.cat.auth.apikey;

import java.util.Optional;

/**
 * Interface to abstract the retrieval of details associated with an API Key allowing different
 * mechanism to be supported, but primarily to ease testing.
 */
public interface ApiKeyDetailsProvider {

  Optional<ApiKeyDetails> findDetailsByKey(String key);
}
