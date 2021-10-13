package uk.gov.crowncommercial.dts.scale.cat.service;

import static java.time.Duration.ofSeconds;
import static java.util.Optional.ofNullable;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static uk.gov.crowncommercial.dts.scale.cat.config.AgreementsServiceAPIConfig.KEY_URI_TEMPLATE;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import lombok.RequiredArgsConstructor;
import uk.gov.crowncommercial.dts.scale.cat.config.AgreementsServiceAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.exception.JaggaerApplicationException;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.DataTemplate;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.TemplateCriteria;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.EventType;

/**
 *
 */
@Service
@RequiredArgsConstructor
public class AgreementsService {

  private final WebClient agreementsServiceWebClient;

  private final AgreementsServiceAPIConfig agreementServiceAPIConfig;

  public List<DataTemplate> getLotEventTypeDataTemplates(final String agreementId,
      final String lotId, final EventType eventType) {

    final var getLotEventTypeDataTemplatesUri =
        agreementServiceAPIConfig.getGetLotEventTypeDataTemplates().get(KEY_URI_TEMPLATE);

    final ParameterizedTypeReference<List<List<TemplateCriteria>>> typeRefTemplateCriteria =
        new ParameterizedTypeReference<>() {};

    final var dataTemplates = ofNullable(agreementsServiceWebClient.get()
        .uri(getLotEventTypeDataTemplatesUri, agreementId, lotId, eventType.getValue()).retrieve()
        .bodyToMono(typeRefTemplateCriteria)
        .block(ofSeconds(agreementServiceAPIConfig.getTimeoutDuration())))
            .orElseThrow(() -> new JaggaerApplicationException(INTERNAL_SERVER_ERROR.value(),
                "Unexpected error retrieving RFI template from AS"));

    return dataTemplates.stream().map(tcs -> DataTemplate.builder().criteria(tcs).build())
        .collect(Collectors.toList());
  }

}
