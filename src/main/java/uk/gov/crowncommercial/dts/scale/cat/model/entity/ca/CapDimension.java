package uk.gov.crowncommercial.dts.scale.cat.model.entity.ca;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import lombok.*;
import lombok.experimental.FieldDefaults;

/**
*
*/
@Entity
@Table(name = "cap_dimensions")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CapDimension {

  @Id
  @Column(name = "dimension_name")
  private String name;

  @Column(name = "dimension_descr")
  private String description;
}
