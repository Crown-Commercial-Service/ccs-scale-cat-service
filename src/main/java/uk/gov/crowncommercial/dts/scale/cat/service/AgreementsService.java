package uk.gov.crowncommercial.dts.scale.cat.service;

import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import uk.gov.crowncommercial.dts.scale.cat.clients.AgreementsClient;
import uk.gov.crowncommercial.dts.scale.cat.exception.AgreementsServiceApplicationException;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.*;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.ViewEventType;

/**
 * Agreements Service API Service layer. Handles interactions with the external service
 */
@Service
@Slf4j
public class AgreementsService {
  @Value("${config.external.agreementsService.apiKey}")
  public String serviceApiKey;

  @Autowired
  AgreementsClient agreementsClient;

  /**
   * Get the Event Type Data Templates for a given Lot for a given Agreement
   */
  @Cacheable(value = "agreementsCache", key = "#root.methodName + '-' + #agreementId + '-' + #lotId + '-' + #eventType.value")
  public List<DataTemplate> getLotEventTypeDataTemplates(final String agreementId, final String lotId, final ViewEventType eventType) {
    // Call the Agreements Service to request the data templates for the given agreement, lot and event type, first formatting the lot ID
    String formattedLotId = formatLotIdForAgreementService(lotId);
    String exceptionFormat = "Unexpected error retrieving " + eventType.name() + " template from AS for Lot " + lotId + " and Agreement " + agreementId;

    try {
      List<DataTemplate> model = agreementsClient.getEventDataTemplates(agreementId, formattedLotId, eventType.getValue(), serviceApiKey);

      // Return the model if possible, otherwise throw an error
      if (model != null) {
        return model;
      } else {
        log.warn("Empty response for Data Templates from Agreement Service for " + agreementId + ", lot " + formattedLotId + ", event type " + eventType.name());
        throw new AgreementsServiceApplicationException(exceptionFormat);
      }
    } catch (Exception ex) {
      log.error("Error getting Data Templates from Agreement Service for " + agreementId + ", lot " + formattedLotId + ", event type " + eventType.name(), ex);
      throw new AgreementsServiceApplicationException(exceptionFormat);
    }
  }

  /**
   * Gets the details of the suppliers for a given Lot of a given Agreement
   */
  @Cacheable(value = "agreementsCache", key = "#root.methodName + '-' + #agreementId + '-' + #lotId")
  public Collection<LotSupplier> getLotSuppliers(final String agreementId, final String lotId) {
    // Call the Agreements Service to request the details of the suppliers attached to a given lot of a given agreement, first formatting the lot ID
    String formattedLotId = formatLotIdForAgreementService(lotId);

    try {
      Collection<LotSupplier> model = agreementsClient.getLotSuppliers(agreementId, formattedLotId, serviceApiKey);

      // Return the model if it has results, otherwise return an empty set
      return model != null ? model : Set.of();
    } catch (Exception ex) {
      log.error("Error getting Lot Suppliers from Agreement Service for " + agreementId + ", lot " + formattedLotId, ex);
      return Set.of();
    }
  }

  /**
   * Gets the details of a given Agreement
   */
  @Cacheable(value = "agreementsCache", key = "#root.methodName + '-' + #agreementId")
  public AgreementDetail getAgreementDetails(final String agreementId) {
    // Call the Agreements Service to request the details of the given agreement
    try {
      AgreementDetail model = agreementsClient.getAgreementDetail(agreementId, serviceApiKey);

      if (model != null) {
        return model;
      } else {
        log.warn("Empty response for Agreement Details from Agreement Service for " + agreementId);
      }
    } catch (Exception ex) {
      log.error("Error getting Agreement Detail from Agreement Service for " + agreementId, ex);
    }

    // If we've not got a response by this point, something went wrong so throw an exception
    throw new NoSuchElementException();
  }

  /**
   * Get the details of a given Lot for an Agreement
   */
  @Cacheable(value = "agreementsCache", key = "#root.methodName + '-' + #agreementId + '-' + #lotId")
  public LotDetail getLotDetails(final String agreementId, final String lotId) {
    // Call the Agreements Service to request the details of the given lot for the given agreement, formatting the Lot ID first
    String formattedLotId = formatLotIdForAgreementService(lotId);
    String exceptionFormat = "Lot with ID: [" + formattedLotId + "] for CA: [" + agreementId + "] not found in AS";

    try {
      LotDetail model = agreementsClient.getLotDetail(agreementId, formattedLotId, serviceApiKey);

      // Return the model if possible, otherwise throw an error
      if (model != null) {
        return model;
      } else {
        log.warn("Empty response for Lot Details from Agreement Service for " + agreementId + ", lot " + formattedLotId);
        throw new AgreementsServiceApplicationException(exceptionFormat);
      }
    } catch (Exception ex) {
      log.error("Error getting Lot Details from Agreement Service for " + agreementId + ", lot " + formattedLotId, ex);
      throw new AgreementsServiceApplicationException(exceptionFormat);
    }
  }

  /**
   * Get Event Types for a given Agreement and Lot
   */
  @Cacheable(value = "agreementsCache", key = "#root.methodName + '-' + #agreementId + '-' + #lotId")
  public Collection<LotEventType> getLotEventTypes(final String agreementId, final String lotId) {
    // Call the Agreements Service to request the event types for the given lot and agreement, formatting the Lot ID first
    String formattedLotId = formatLotIdForAgreementService(lotId);

    try {
      Collection<LotEventType> model = agreementsClient.getLotEventTypes(agreementId, formattedLotId, serviceApiKey);

      // Return the model if it has results, otherwise return an empty set
      return model != null ? model : Set.of();
    } catch (Exception ex) {
      log.error("Error getting Event Types from Agreement Service for " + agreementId + ", lot " + formattedLotId, ex);
      return Set.of();
    }
  }

  /**
   * Strip out legacy formatting from the supplied Lot ID, so that legacy projects can continue to work with the updated Agreement Service
   */
  private String formatLotIdForAgreementService(String lotId) {
    return lotId.replace("Lot ", "");
  }
}