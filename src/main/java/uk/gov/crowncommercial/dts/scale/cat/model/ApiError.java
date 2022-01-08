package uk.gov.crowncommercial.dts.scale.cat.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * API Error Wrapper.
 */
@Data
public class ApiError {

  @JsonProperty
  private final String status;

  @JsonProperty
  private final String title;

  @JsonProperty
  private final String detail;
}
