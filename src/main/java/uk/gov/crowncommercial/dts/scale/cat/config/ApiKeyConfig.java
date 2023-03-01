package uk.gov.crowncommercial.dts.scale.cat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import lombok.Data;

/**
 * Spring configuration class for the API Key authentication mechanism.
 */
@ConfigurationProperties(prefix = "config.auth.apikey", ignoreUnknownFields = true)
@Data
public class ApiKeyConfig {

  /** HTTP header used for passing the API Key */
  private String header;
  
  /** single API Key */
  private String key;
  
  /** comma separated list of authorities for the given API Key */
  private String authorities;
}
