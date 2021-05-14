package uk.gov.crowncommercial.dts.scale.cat.model.jaggaer;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value
public class AdditionalInfoValue {

  @JsonProperty("value")
  String value;
}
