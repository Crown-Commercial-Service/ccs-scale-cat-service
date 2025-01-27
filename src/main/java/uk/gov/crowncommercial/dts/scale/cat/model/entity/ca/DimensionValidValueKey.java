package uk.gov.crowncommercial.dts.scale.cat.model.entity.ca;

import java.io.Serializable;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Data;

/**
 * Compound Key.
 */
@Data
@Embeddable
public class DimensionValidValueKey implements Serializable {

  /**
   *
   */
  private static final long serialVersionUID = 1L;

  @Column(name = "dimension_id")
  private Integer dimensionId;

  @Column(name = "valid_value_code")
  private String valueCode;

}
