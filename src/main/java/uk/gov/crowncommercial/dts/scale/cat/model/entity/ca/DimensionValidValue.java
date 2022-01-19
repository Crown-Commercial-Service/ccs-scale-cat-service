package uk.gov.crowncommercial.dts.scale.cat.model.entity.ca;

import javax.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.Timestamps;

/**
*
*/
@Entity
@Table(name = "dimension_valid_values")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DimensionValidValue {

  @EmbeddedId
  DimensionValidValueKey key;

  @Column(name = "valid_value_name")
  private String valueName;

  @Column(name = "valid_value_descr")
  private String valueDescription;

  @Embedded
  private Timestamps timestamps;
}
