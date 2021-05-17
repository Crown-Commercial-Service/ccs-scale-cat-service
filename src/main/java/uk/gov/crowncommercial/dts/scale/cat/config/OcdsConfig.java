package uk.gov.crowncommercial.dts.scale.cat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Data;

/**
 *
 */
@Configuration
@ConfigurationProperties(prefix = "config.ocds", ignoreUnknownFields = true)
@Data
public class OcdsConfig {

  private String authority;
  private String ocidPrefix;

}
