package uk.gov.crowncommercial.dts.scale.cat.clients;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.LotDetail;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.LotEventType;

import java.util.Collection;

/**
 * Web client to interface with the Agreements Service API
 */
@FeignClient(name = "agreementsClient", url = "${config.external.agreements-service.baseUrl}")
public interface AgreementsClient {
    @GetMapping("${config.external.agreementsService.getEventTypesForAgreement.uriTemplate}")
    Collection<LotEventType> getLotEventTypes(@PathVariable("agreement-id") String agreementId, @PathVariable("lot-id") String lotId, @RequestHeader("x-api-key") String apiKey);

    @GetMapping("${config.external.agreementsService.getLotDetailsForAgreement.uriTemplate}")
    LotDetail getLotDetail(@PathVariable("agreement-id") String agreementId, @PathVariable("lot-id") String lotId, @RequestHeader("x-api-key") String apiKey);
}