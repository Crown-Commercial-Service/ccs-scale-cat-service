package uk.gov.crowncommercial.dts.scale.cat.model.entity.ca;

import java.io.Serializable;
import javax.persistence.Column;
import javax.persistence.Embeddable;
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

  @Column(name = "dimension_name")
  private String name;

  @Column(name = "valid_value_code")
  private String valueCode;

}
