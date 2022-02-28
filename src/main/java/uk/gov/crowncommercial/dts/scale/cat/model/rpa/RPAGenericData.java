package uk.gov.crowncommercial.dts.scale.cat.model.rpa;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.experimental.FieldDefaults;

@Data
@Accessors(chain = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RPAGenericData {
  String processInput;
  String processName;
  String profileName;
  String source;
  String sourceId;
  boolean retry;
  @JsonProperty(value = "isSync")
  boolean isSync;
  long requestTimeout;
}