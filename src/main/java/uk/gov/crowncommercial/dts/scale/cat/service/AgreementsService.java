package uk.gov.crowncommercial.dts.scale.cat.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import uk.gov.crowncommercial.dts.scale.cat.model.AgreementSummary;

/**
 * Simple service example to fetch data from the Scale shared Agreements Service
 */
@Service
public class AgreementsService {

  private final RestTemplate restTemplate;

  public AgreementsService(
      @Value("${AGREEMENTS_SERVICE_URL:http://localhost:9010}") final String agreementsSvcUrl,
      @Value("${AGREEMENTS_SERVICE_API_KEY:abc123}") final String agreementsSvcApiKey,
      final RestTemplateBuilder restTemplateBuilder) {

    restTemplate = restTemplateBuilder.rootUri(agreementsSvcUrl)
        .defaultHeader("x-api-key", agreementsSvcApiKey).build();
  }

  public AgreementSummary[] findAll() {
    return restTemplate.getForObject("/agreements", AgreementSummary[].class);
  }

}
