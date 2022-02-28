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
public class AutomationOutputData {

  @JsonProperty("CviewDictionary")
  private CviewDictionary cviewDictionary;

  @JsonProperty("AppName")
  private String appName;

}