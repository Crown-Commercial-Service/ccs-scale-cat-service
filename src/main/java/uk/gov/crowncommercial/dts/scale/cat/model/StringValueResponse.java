package uk.gov.crowncommercial.dts.scale.cat.model;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Value;

/**
 *
 */
@Value
public class StringValueResponse {

  @JsonValue
  String value;

}
