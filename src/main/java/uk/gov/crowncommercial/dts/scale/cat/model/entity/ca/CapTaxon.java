package uk.gov.crowncommercial.dts.scale.cat.model.entity.ca;

import javax.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

/**
*
*/
@Entity
@Table(name = "cap_taxons")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CapTaxon {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "taxon_id")
  Integer id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "taxonomy_id")
  CapTaxonomy taxonomy;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "parent_taxon_id")
  CapTaxonomy parentTaxonomy;

  @Column(name = "taxon_name")
  private String name;

  @Column(name = "permalink")
  private String link;

  @Column(name = "taxon_descr")
  private String description;
}
