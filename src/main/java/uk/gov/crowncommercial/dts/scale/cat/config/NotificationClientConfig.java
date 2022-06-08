package uk.gov.crowncommercial.dts.scale.cat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import lombok.Data;
import uk.gov.service.notify.NotificationClient;

/**
 * GOV.UK Notify client & config
 */
@Configuration
@ConfigurationProperties(prefix = "config.external.notification", ignoreUnknownFields = true)
@Data
public class NotificationClientConfig {

  private String apiKey;

  @Bean
  public NotificationClient notificationClient() {
    return new NotificationClient(apiKey);
  }

}
