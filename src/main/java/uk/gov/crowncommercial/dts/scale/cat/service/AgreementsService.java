package uk.gov.crowncommercial.dts.scale.cat.service;

import static java.time.Duration.ofSeconds;
import static java.util.Optional.ofNullable;
import static uk.gov.crowncommercial.dts.scale.cat.config.AgreementsServiceAPIConfig.KEY_URI_TEMPLATE;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import lombok.RequiredArgsConstructor;
import reactor.util.retry.Retry;
import uk.gov.crowncommercial.dts.scale.cat.config.AgreementsServiceAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.config.Constants;
import uk.gov.crowncommercial.dts.scale.cat.exception.AgreementsServiceApplicationException;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.DataTemplate;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.LotSupplier;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.TemplateCriteria;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.ViewEventType;

/**
 * AS API Service layer. Handles interactions with the external Agreements Service.
 */
@Service
@RequiredArgsConstructor
public class AgreementsService {

  private final WebClient agreementsServiceWebClient;
  private final AgreementsServiceAPIConfig agreementServiceAPIConfig;
  private final WebclientWrapper webclientWrapper;

  public List<DataTemplate> getLotEventTypeDataTemplates(final String agreementId,
      final String lotId, final ViewEventType eventType) {

    var getLotEventTypeDataTemplatesUri =
        agreementServiceAPIConfig.getGetLotEventTypeDataTemplates().get(KEY_URI_TEMPLATE);

    final ParameterizedTypeReference<List<List<TemplateCriteria>>> typeRefTemplateCriteria =
        new ParameterizedTypeReference<>() {};

    var dataTemplates = ofNullable(agreementsServiceWebClient.get()
        .uri(getLotEventTypeDataTemplatesUri, agreementId, lotId, eventType.getValue()).retrieve()
        .bodyToMono(typeRefTemplateCriteria)
        .retryWhen(Retry.fixedDelay(Constants.WEBCLIENT_DEFAULT_RETRIES,
            Duration.ofSeconds(Constants.WEBCLIENT_DEFAULT_DELAY)))
        .block(ofSeconds(agreementServiceAPIConfig.getTimeoutDuration())))
            .orElseThrow(() -> new AgreementsServiceApplicationException(
                "Unexpected error retrieving RFI template from AS"));

    return dataTemplates.stream().map(tcs -> DataTemplate.builder().criteria(tcs).build())
        .collect(Collectors.toList());
  }

  public Set<LotSupplier> getLotSuppliers(final String agreementId, final String lotId) {
    var getLotSuppliersUri = agreementServiceAPIConfig.getGetLotSuppliers().get(KEY_URI_TEMPLATE);

    var lotSuppliers =
        webclientWrapper.getOptionalResource(LotSupplier[].class, agreementsServiceWebClient,
            agreementServiceAPIConfig.getTimeoutDuration(), getLotSuppliersUri, agreementId, lotId);

    return lotSuppliers.isPresent() ? Set.of(lotSuppliers.get()) : Set.of();
  }

}
