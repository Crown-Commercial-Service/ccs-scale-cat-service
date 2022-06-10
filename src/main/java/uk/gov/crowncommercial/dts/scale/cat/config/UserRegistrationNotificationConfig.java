package uk.gov.crowncommercial.dts.scale.cat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Data;

/**
 * User registration (GOV.UK Notify) client config
 */
@Configuration
@ConfigurationProperties(prefix = "config.external.notification.user-registration",
    ignoreUnknownFields = true)
@Data
public class UserRegistrationNotificationConfig {

  private String templateId;
  private String targetEmail;

}
