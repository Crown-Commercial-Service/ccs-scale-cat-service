package uk.gov.crowncommercial.dts.scale.cat.model.entity.ca;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.persistence.Id;
import lombok.Data;

/**
 * Compound Key.
 */
@Data
@Embeddable
public class CapDimensionValidValueKey {

  @Id
  @Column(name = "dimension_name")
  private String name;

  @Column(name = "valid_value_code")
  private String valueCode;

}
