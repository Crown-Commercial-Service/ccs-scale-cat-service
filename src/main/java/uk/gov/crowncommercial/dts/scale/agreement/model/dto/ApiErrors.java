package uk.gov.crowncommercial.dts.scale.agreement.model.dto;

import lombok.Data;

import java.util.List;

/**
 * Collection of {@link ApiError} objects.
 */
@Data
public class ApiErrors {

  private final List<ApiError> errors;
  private final String description;

}
