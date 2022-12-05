package uk.gov.crowncommercial.dts.scale.cat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Data;

/**
 *
 */
@Configuration
@ConfigurationProperties(prefix = "config.flags", ignoreUnknownFields = true)
@Data
public class ApplicationFlagsConfig {

  private Boolean devMode;
  private Boolean resolveBuyerUsersBySSO;

  private Boolean expAsyncJaggaer = true;

}
