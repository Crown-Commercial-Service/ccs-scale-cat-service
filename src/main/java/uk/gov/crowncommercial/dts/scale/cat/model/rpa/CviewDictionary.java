package uk.gov.crowncommercial.dts.scale.cat.model.rpa;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
*
*/
@Value
@Builder
@Jacksonized
public class CviewDictionary {

  @JsonProperty("ErrorDescription")
  private String errorDescription;

  @JsonProperty("IsError")
  private String isError;

  @JsonProperty("Status")
  private String status;

  @JsonProperty("isTrue")
  private String isTrue;
}
