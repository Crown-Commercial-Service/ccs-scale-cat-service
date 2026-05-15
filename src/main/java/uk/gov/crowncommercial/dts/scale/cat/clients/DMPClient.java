package uk.gov.crowncommercial.dts.scale.cat.clients;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import uk.gov.crowncommercial.dts.scale.cat.model.dmp.SupplierDetail;

@FeignClient(name = "dmpClient", url = "${config.external.dmp-api.base-url}")
public interface DMPClient {

  @GetMapping("${config.external.dmp-api.supplier-detail-path}")
  SupplierDetail getSupplierDetails(
      @PathVariable("supplier-id") final String supplierId,
      @RequestHeader("Authorization") final String bearerToken);
}
