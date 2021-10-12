package uk.gov.crowncommercial.dts.scale.cat.service;

import static java.time.Duration.ofSeconds;
import static java.util.Optional.ofNullable;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static uk.gov.crowncommercial.dts.scale.cat.config.AgreementsServiceAPIConfig.ENDPOINT;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import lombok.RequiredArgsConstructor;
import uk.gov.crowncommercial.dts.scale.cat.config.AgreementsServiceAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.exception.JaggaerApplicationException;
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

  public Set<TemplateCriteria> getLotEventTemplateCriteria(final String agreementId,
      final String lotId, final EventType eventType) {

    final var getLotEventRFITemplateUri =
        agreementServiceAPIConfig.getGetLotEventRFITemplate().get(ENDPOINT);

    final var templateCriteria = ofNullable(agreementsServiceWebClient.get()
        .uri(getLotEventRFITemplateUri, agreementId, lotId, eventType.getValue()).retrieve()
        .bodyToMono(TemplateCriteria[].class)
        .block(ofSeconds(agreementServiceAPIConfig.getTimeoutDuration())))
            .orElseThrow(() -> new JaggaerApplicationException(INTERNAL_SERVER_ERROR.value(),
                "Unexpected error retrieving RFI template from AS"));

    return Set.of(templateCriteria);
  }

}
