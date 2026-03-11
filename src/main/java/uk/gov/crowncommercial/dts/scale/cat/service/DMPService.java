package uk.gov.crowncommercial.dts.scale.cat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.gov.crowncommercial.dts.scale.cat.clients.DMPClient;
import uk.gov.crowncommercial.dts.scale.cat.model.dmp.SupplierDetail;
import uk.gov.crowncommercial.dts.scale.cat.utils.ApplicationUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class DMPService {

  private final DMPClient dmpClient;

  @Value("${config.external.dmp-api.bearer-token}")
  private String accessToken;

  public SupplierDetail getSupplierDetails(final String supplierId) {
    return dmpClient.getSupplierDetails(
        supplierId, ApplicationUtils.formatAccessToken(accessToken));
  }
}
