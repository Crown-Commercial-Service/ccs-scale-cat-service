package uk.gov.crowncommercial.dts.scale.cat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Data;

/**
 *
 */
@Configuration
@ConfigurationProperties(prefix = "config.external.jaggaer.rpa", ignoreUnknownFields = true)
@Data
public class RPAAPIConfig {

  // Access keys
  private String baseUrl;
  private String authenticationUrl;
  private String accessUrl;
  private String userName;
  private String userPwd;

  // RPA input keys
  private String source;
  private String sourceId;
  private String profileName;
  private Integer timeoutDuration;
  private Long requestTimeout;

}
