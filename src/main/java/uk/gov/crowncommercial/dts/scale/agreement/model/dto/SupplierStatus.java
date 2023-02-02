package uk.gov.crowncommercial.dts.scale.agreement.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Supplier status
 */
public enum SupplierStatus {

  @JsonProperty("active")
  ACTIVE,

  @JsonProperty("suspended")
  SUSPENDED,

  @JsonProperty("excluded")
  EXCLUDED;

}
