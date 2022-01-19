package uk.gov.crowncommercial.dts.scale.cat.model.entity.ca;

import javax.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.Timestamps;

/**
*
*/
@Entity
@Table(name = "assessment_selections")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AssessmentSelection {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "assessment_selection_id")
  Integer id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "assessment_id")
  Assessment assessment;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "requirement_taxon_id")
  RequirementTaxon requirementTaxon;

  @Column(name = "dimension_name")
  private String dimensionName;

  @Column(name = "weighting_pct")
  private Double weightingPercentage;

  @Embedded
  private Timestamps timestamps;
}
