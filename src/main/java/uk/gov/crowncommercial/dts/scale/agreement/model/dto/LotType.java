package uk.gov.crowncommercial.dts.scale.agreement.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * LotType.
 */
public enum LotType {

  @JsonProperty("product")
  PRODUCT,

  @JsonProperty("service")
  SERVICE,

  @JsonProperty("product and service")
  PRODUCT_AND_SERVICE

}
