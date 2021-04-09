package uk.gov.crowncommercial.dts.scale.cat.service;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import lombok.RequiredArgsConstructor;
import uk.gov.crowncommercial.dts.scale.cat.model.AgreementSummary;

/**
 * Simple service example to fetch data from the Scale shared Agreements Service
 */
@Service
@RequiredArgsConstructor
public class ProjectService {

  private final RestTemplate restTemplate;

  public AgreementSummary[] findAll() {
    return restTemplate.getForObject("/agreements", AgreementSummary[].class);
  }

}
