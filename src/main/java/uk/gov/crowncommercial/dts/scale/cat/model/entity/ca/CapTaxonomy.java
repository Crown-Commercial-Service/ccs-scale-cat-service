package uk.gov.crowncommercial.dts.scale.cat.model.entity.ca;

import javax.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

/**
*
*/
@Entity
@Table(name = "cap_taxonomies")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CapTaxonomy {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "taxonomy_id")
  Integer id;

  @Column(name = "taxonomy_name")
  private String name;
}
