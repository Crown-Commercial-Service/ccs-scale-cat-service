package uk.gov.crowncommercial.dts.scale.cat.service;

import static java.time.Duration.ofSeconds;
import static java.util.Optional.ofNullable;
import static uk.gov.crowncommercial.dts.scale.cat.config.AgreementsServiceAPIConfig.KEY_URI_TEMPLATE;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import lombok.RequiredArgsConstructor;
import reactor.util.retry.Retry;
import uk.gov.crowncommercial.dts.scale.cat.config.AgreementsServiceAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.config.Constants;
import uk.gov.crowncommercial.dts.scale.cat.exception.AgreementsServiceApplicationException;
import uk.gov.crowncommercial.dts.scale.cat.interceptors.TrackExecutionTime;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.*;
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

  @TrackExecutionTime
  @Cacheable(value = "getLotEventTypeDataTemplates",  key = "{#agreementId, #lotId,#eventType.value}")
  public List<DataTemplate> getLotEventTypeDataTemplates(final String agreementId,
      final String lotId, final ViewEventType eventType) {

    var getLotEventTypeDataTemplatesUri =
        agreementServiceAPIConfig.getGetLotEventTypeDataTemplates().get(KEY_URI_TEMPLATE);

    final ParameterizedTypeReference<List<DataTemplate>> typeRefTemplateCriteria =
        new ParameterizedTypeReference<>() {};

    try {
      var dataTemplates = ofNullable(agreementsServiceWebClient.get()
              .uri(getLotEventTypeDataTemplatesUri, agreementId, lotId, eventType.getValue()).retrieve()
              .bodyToMono(typeRefTemplateCriteria)
              .onErrorMap(IOException.class, UncheckedIOException::new)
              .retryWhen(Retry
                      .fixedDelay(Constants.WEBCLIENT_DEFAULT_RETRIES,
                              Duration.ofSeconds(Constants.WEBCLIENT_DEFAULT_DELAY))
                      .filter(WebclientWrapper::is5xxServerError))
              .block(ofSeconds(agreementServiceAPIConfig.getTimeoutDuration())))
              .orElseThrow(() -> new AgreementsServiceApplicationException(String
                      .format("Unexpected error retrieving {} template from AS", eventType.name())));
      return dataTemplates;
    }catch(Throwable e){
      // TODO This try-catch block and the fallbackQuery function to be removed after agreement service upgraded.
      return fallbackQuery(agreementId, lotId, eventType);
    }
  }

  private List<DataTemplate> fallbackQuery(final String agreementId,
                                           final String lotId, final ViewEventType eventType) {

    var getLotEventTypeDataTemplatesUri =
            agreementServiceAPIConfig.getGetLotEventTypeDataTemplates().get(KEY_URI_TEMPLATE);

    final ParameterizedTypeReference<List<List<TemplateCriteria>>> typeRefTemplateCriteria =
            new ParameterizedTypeReference<>() {
            };

    var dataTemplates = ofNullable(agreementsServiceWebClient.get()
            .uri(getLotEventTypeDataTemplatesUri, agreementId, lotId, eventType.getValue()).retrieve()
            .bodyToMono(typeRefTemplateCriteria)
            .onErrorMap(IOException.class, UncheckedIOException::new)
            .retryWhen(Retry
                    .fixedDelay(Constants.WEBCLIENT_DEFAULT_RETRIES,
                            Duration.ofSeconds(Constants.WEBCLIENT_DEFAULT_DELAY))
                    .filter(WebclientWrapper::is5xxServerError))
            .block(ofSeconds(agreementServiceAPIConfig.getTimeoutDuration())))
            .orElseThrow(() -> new AgreementsServiceApplicationException(String
                    .format("Unexpected error retrieving {} template from AS", eventType.name())));

    return dataTemplates.stream().map(tcs -> DataTemplate.builder().criteria(tcs).build())
            .collect(Collectors.toList());

  }

  @TrackExecutionTime
  @Cacheable(value = "getLotSuppliers",  key = "{#agreementId, #lotId}")
  public Collection<LotSupplier> getLotSuppliers(final String agreementId, final String lotId) {
    var getLotSuppliersUri = agreementServiceAPIConfig.getGetLotSuppliers().get(KEY_URI_TEMPLATE);

    var lotSuppliers =
        webclientWrapper.getOptionalResource(LotSupplier[].class, agreementsServiceWebClient,
            agreementServiceAPIConfig.getTimeoutDuration(), getLotSuppliersUri, agreementId, lotId);

    return lotSuppliers.isPresent() ? Set.of(lotSuppliers.get()) : Set.of();
  }


  @TrackExecutionTime
  @Cacheable(value = "getAgreementDetails", key = "#agreementId")
  public AgreementDetail getAgreementDetails(final String agreementId) {
    var agreementDetailsUri =
        agreementServiceAPIConfig.getGetAgreementDetail().get(KEY_URI_TEMPLATE);

    var agreementDetail =
        webclientWrapper.getOptionalResource(AgreementDetail.class, agreementsServiceWebClient,
            agreementServiceAPIConfig.getTimeoutDuration(), agreementDetailsUri, agreementId);

    return agreementDetail.orElseThrow();
  }

  @TrackExecutionTime
  @Cacheable(value = "getLotDetails", key = "{#agreementId, #lotId}")
  public LotDetail getLotDetails(final String agreementId, final String lotId) {
    var lotDetailUri =
        agreementServiceAPIConfig.getGetLotDetailsForAgreement().get(KEY_URI_TEMPLATE);

    var lotDetail =
        webclientWrapper.getOptionalResource(LotDetail.class, agreementsServiceWebClient,
            agreementServiceAPIConfig.getTimeoutDuration(), lotDetailUri, agreementId, lotId);

    return lotDetail.orElseThrow(() -> new AgreementsServiceApplicationException(
        "Lot with ID: [" + lotId + "] for CA: [" + agreementId + "] not found in AS"));
  }
  @TrackExecutionTime
  @Cacheable(value = "getLotEventTypes", key = "{#agreementId, #lotId}")
  public Collection<LotEventType> getLotEventTypes(final String agreementId, final String lotId) {
    var getLotEventTypesUri =
        agreementServiceAPIConfig.getGetEventTypesForAgreement().get(KEY_URI_TEMPLATE);

    var lotEventTypes = webclientWrapper.getOptionalResource(LotEventType[].class,
        agreementsServiceWebClient, agreementServiceAPIConfig.getTimeoutDuration(),
        getLotEventTypesUri, agreementId, lotId);

    return lotEventTypes.isPresent() ? Set.of(lotEventTypes.get()) : Set.of();
  }

}
