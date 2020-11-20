package uk.gov.crowncommercial.dts.scale.cat.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 *
 */
@Configuration
public class AgreementsServiceConfig {

  private final RestTemplate restTemplate;

  public AgreementsServiceConfig(
      @Value("${AGREEMENTS_SERVICE_URL:http://localhost:9010}") final String agreementsSvcUrl,
      @Value("${AGREEMENTS_SERVICE_API_KEY:abc123}") final String agreementsSvcApiKey) {

    restTemplate = new RestTemplateBuilder().rootUri(agreementsSvcUrl)
        .defaultHeader("x-api-key", agreementsSvcApiKey).build();
  }

  @Bean
  public RestTemplate restTemplate() {
    return restTemplate;
  }

}
