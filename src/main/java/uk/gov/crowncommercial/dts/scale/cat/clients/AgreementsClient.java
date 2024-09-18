package uk.gov.crowncommercial.dts.scale.cat.clients;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.*;

import java.util.Collection;
import java.util.List;

/**
 * Web client to interface with the Agreements Service API
 */
@FeignClient(name = "agreementsClient", url = "${config.external.agreementsService.baseUrl}")
public interface AgreementsClient {
    @GetMapping("${config.external.agreementsService.getEventTypesForAgreement.uriTemplate}")
    Collection<LotEventType> getLotEventTypes(@PathVariable("agreement-id") String agreementId, @PathVariable("lot-id") String lotId, @RequestHeader("x-api-key") String apiKey);

    @GetMapping("${config.external.agreementsService.getLotDetailsForAgreement.uriTemplate}")
    LotDetail getLotDetail(@PathVariable("agreement-id") String agreementId, @PathVariable("lot-id") String lotId, @RequestHeader("x-api-key") String apiKey);

    @GetMapping("${config.external.agreementsService.getAgreementDetail.uriTemplate}")
    AgreementDetail getAgreementDetail(@PathVariable("agreement-id") String agreementId, @RequestHeader("x-api-key") String apiKey);

    @GetMapping("${config.external.agreementsService.getLotSuppliers.uriTemplate}")
    Collection<LotSupplier> getLotSuppliers(@PathVariable("agreement-id") String agreementId, @PathVariable("lot-id") String lotId, @RequestHeader("x-api-key") String apiKey);

    @GetMapping("${config.external.agreementsService.getLotEventTypeDataTemplates.uriTemplate}")
    List<DataTemplate> getEventDataTemplates(@PathVariable("agreement-id") String agreementId, @PathVariable("lot-id") String lotId, @PathVariable("event-type") String eventType, @RequestHeader("x-api-key") String apiKey);
}