package uk.gov.crowncommercial.dts.scale.cat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Data;

/**
 *
 */
@Configuration
@ConfigurationProperties(prefix = "config.external.jaggaer", ignoreUnknownFields = true)
@Data
public class JaggaerAPIConfig {

  private String baseUrl;

}
