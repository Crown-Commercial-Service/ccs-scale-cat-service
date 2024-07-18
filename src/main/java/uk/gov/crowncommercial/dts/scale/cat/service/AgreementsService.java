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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import lombok.RequiredArgsConstructor;
import reactor.util.retry.Retry;
import uk.gov.crowncommercial.dts.scale.cat.clients.AgreementsClient;
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
  @Value("${config.external.agreements-service.apiKey}")
  public String serviceApiKey;

  private final AgreementsClient agreementsClient;

  private final WebClient agreementsServiceWebClient;
  private final AgreementsServiceAPIConfig agreementServiceAPIConfig;
  private final WebclientWrapper webclientWrapper;

  @TrackExecutionTime
  @Cacheable(value = "getLotEventTypeDataTemplates",  key = "{#agreementId, #lotId,#eventType.value}")
  public List<DataTemplate> getLotEventTypeDataTemplates(final String agreementId, final String lotId, final ViewEventType eventType) {
    String formattedLotId = formatLotIdForAgreementService(lotId);

    var getLotEventTypeDataTemplatesUri =
        agreementServiceAPIConfig.getGetLotEventTypeDataTemplates().get(KEY_URI_TEMPLATE);

    final ParameterizedTypeReference<List<DataTemplate>> typeRefTemplateCriteria =
        new ParameterizedTypeReference<>() {};

    try {
      var dataTemplates = ofNullable(agreementsServiceWebClient.get()
              .uri(getLotEventTypeDataTemplatesUri, agreementId, formattedLotId, eventType.getValue()).retrieve()
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
      return fallbackQuery(agreementId, formattedLotId, eventType);
    }
  }

  private List<DataTemplate> fallbackQuery(final String agreementId, final String lotId, final ViewEventType eventType) {
    String formattedLotId = formatLotIdForAgreementService(lotId);

    var getLotEventTypeDataTemplatesUri =
            agreementServiceAPIConfig.getGetLotEventTypeDataTemplates().get(KEY_URI_TEMPLATE);

    final ParameterizedTypeReference<List<List<TemplateCriteria>>> typeRefTemplateCriteria =
            new ParameterizedTypeReference<>() {
            };

    var dataTemplates = ofNullable(agreementsServiceWebClient.get()
            .uri(getLotEventTypeDataTemplatesUri, agreementId, formattedLotId, eventType.getValue()).retrieve()
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
    String formattedLotId = formatLotIdForAgreementService(lotId);
    var getLotSuppliersUri = agreementServiceAPIConfig.getGetLotSuppliers().get(KEY_URI_TEMPLATE);

    var lotSuppliers =
        webclientWrapper.getOptionalResource(LotSupplier[].class, agreementsServiceWebClient,
            agreementServiceAPIConfig.getTimeoutDuration(), getLotSuppliersUri, agreementId, formattedLotId);

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
    String formattedLotId = formatLotIdForAgreementService(lotId);
    var lotDetailUri =
        agreementServiceAPIConfig.getGetLotDetailsForAgreement().get(KEY_URI_TEMPLATE);

    var lotDetail =
        webclientWrapper.getOptionalResource(LotDetail.class, agreementsServiceWebClient,
            agreementServiceAPIConfig.getTimeoutDuration(), lotDetailUri, agreementId, formattedLotId);

    return lotDetail.orElseThrow(() -> new AgreementsServiceApplicationException(
        "Lot with ID: [" + formattedLotId + "] for CA: [" + agreementId + "] not found in AS"));
  }

  /**
   * Get Event Types for a given Agreement and Lot
   */
  @Cacheable(value = "getLotEventTypes", key = "{#agreementId, #lotId}")
  public Collection<LotEventType> getLotEventTypes(final String agreementId, final String lotId) {
    // Call the Agreements Service to request the event types for the given lot and agreement, formatting the Lot ID first
    String formattedLotId = formatLotIdForAgreementService(lotId);
    Collection<LotEventType> model = agreementsClient.getLotEventTypes(agreementId, formattedLotId, serviceApiKey);

    // Return the model if it has results, otherwise return an empty set
    return model != null ? model : Set.of();
  }

  /**
   * Strip out legacy formatting from the supplied Lot ID, so that legacy projects can continue to work with the updated Agreement Service
   */
  private String formatLotIdForAgreementService(String lotId) {
    return lotId.replace("Lot ", "");
  }
}