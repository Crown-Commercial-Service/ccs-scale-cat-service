package uk.gov.crowncommercial.dts.scale.cat.model.entity.ca;

import java.util.Set;
import javax.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.Timestamps;

/**
*
*/
@Entity
@Table(name = "assessment_taxons")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AssessmentTaxon {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "assessment_taxon_id")
  Integer id;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "assessment_tool_id")
  AssessmentTool tool;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "parent_assessment_taxon_id")
  AssessmentTaxon parentTaxon;

  @EqualsAndHashCode.Exclude
  @OneToMany(fetch = FetchType.EAGER, cascade = CascadeType.ALL)
  @JoinColumn(name = "parent_assessment_taxon_id")
  Set<AssessmentTaxon> assessmentTaxons;

  @EqualsAndHashCode.Exclude
  @ManyToMany(fetch = FetchType.EAGER)
  @JoinTable(name = "assessment_taxon_dimensions",
      joinColumns = @JoinColumn(name = "assessment_taxon_id"),
      inverseJoinColumns = @JoinColumn(name = "dimension_id"))
  Set<DimensionEntity> dimensions;

  @EqualsAndHashCode.Exclude
  @OneToMany(fetch = FetchType.EAGER, cascade = CascadeType.ALL)
  @JoinColumn(name = "assessment_taxon_id")
  Set<RequirementTaxon> requirementTaxons;

  @Column(name = "assessment_taxon_name")
  private String name;

  @Column(name = "permalink")
  private String link;

  @Column(name = "assessment_taxon_descr")
  private String description;

  @Embedded
  private Timestamps timestamps;
}
