package uk.gov.crowncommercial.dts.scale.cat.model.jaggaer;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value
public class AdditionalInfoValues {

  @JsonProperty("value")
  List<AdditionalInfoValue> valueList;
}
