package uk.gov.crowncommercial.dts.scale.cat.service;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import lombok.RequiredArgsConstructor;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.TemplateCriteria;

/**
 *
 */
@Service
@RequiredArgsConstructor
public class AgreementsService {

  private final WebClient agreementsServiceWebClient;

  public TemplateCriteria getProcurementQuestionTemplate() {

  }

}
