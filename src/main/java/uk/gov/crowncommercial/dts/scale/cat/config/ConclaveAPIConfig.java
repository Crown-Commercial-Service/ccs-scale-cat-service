package uk.gov.crowncommercial.dts.scale.cat.config;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Data;

/**
 *
 */
@Configuration
@ConfigurationProperties(prefix = "config.external.conclave-wrapper", ignoreUnknownFields = true)
@Data
public class ConclaveAPIConfig {

  public static final String KEY_URI_TEMPLATE = "uriTemplate";

  private String baseUrl;
  private String apiKey;
  private Integer timeoutDuration;
  private String buyerRoleKey;
  private String supplierRoleKey;
  private Map<String, String> getUser;
  private Map<String, String> getUserContacts;
  private Map<String, String> getOrganisation;
}
