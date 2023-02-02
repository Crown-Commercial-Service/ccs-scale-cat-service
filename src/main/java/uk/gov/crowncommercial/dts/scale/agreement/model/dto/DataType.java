package uk.gov.crowncommercial.dts.scale.agreement.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum DataType {

  @JsonProperty("string")
  STRING,

  @JsonProperty("integer")
  INTEGER,

  @JsonProperty("number")
  NUMBER,

  @JsonProperty("date")
  DATE
}
