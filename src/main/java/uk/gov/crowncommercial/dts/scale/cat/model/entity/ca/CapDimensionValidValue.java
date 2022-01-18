package uk.gov.crowncommercial.dts.scale.cat.model.entity.ca;

import javax.persistence.Column;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.Table;
import lombok.*;
import lombok.experimental.FieldDefaults;

/**
*
*/
@Entity
@Table(name = "cap_dimension_valid_values")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CapDimensionValidValue {

  @EmbeddedId
  CapDimensionValidValueKey key;

  @Column(name = "valid_value_name")
  private String valueName;

  @Column(name = "valid_value_descr")
  private String valueDescription;

}
