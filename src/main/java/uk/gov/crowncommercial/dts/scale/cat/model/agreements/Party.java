package uk.gov.crowncommercial.dts.scale.cat.model.agreements;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 *
 */
public enum Party {

  @JsonProperty("buyer")
  BUYER,

  @JsonProperty("tenderer")
  TENDERER;

}
