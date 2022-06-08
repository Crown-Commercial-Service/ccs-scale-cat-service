package uk.gov.crowncommercial.dts.scale.cat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import uk.gov.service.notify.NotificationClient;

/**
 * GOV.UK Notify client & config
 */
@ConfigurationProperties(prefix = "config.external.notification", ignoreUnknownFields = true)
@RequiredArgsConstructor
@Value
public class NotificationClientConfig {

  private final String apiKey;
  private final String userRegistrationTargetEmail;
  private final String userRegistrationTemplateId;

  @Bean
  public NotificationClient notificationClient(final String apiKey) {
    return new NotificationClient(apiKey);
  }

}
