package uk.gov.crowncommercial.dts.scale.cat.model.entity.ca;

import javax.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

/**
*
*/
@Entity
@Table(name = "cap_taxon_dimensions")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CapTaxonDimension {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "taxon_dimension_id")
  Integer id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "taxon_id")
  CapTaxon taxon;

  @Column(name = "dimension_name")
  private String name;
}
