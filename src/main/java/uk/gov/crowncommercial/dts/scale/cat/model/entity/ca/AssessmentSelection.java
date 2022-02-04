package uk.gov.crowncommercial.dts.scale.cat.model.entity.ca;

import java.math.BigDecimal;
import java.util.Set;
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
@EqualsAndHashCode(exclude = "assessment")
public class AssessmentSelection {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "assessment_selection_id")
  Integer id;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "assessment_id")
  AssessmentEntity assessment;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "dimension_id")
  DimensionEntity dimension;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "requirement_taxon_id")
  RequirementTaxon requirementTaxon;

  @OneToMany(mappedBy = "assessmentSelection", fetch = FetchType.EAGER, cascade = CascadeType.ALL,
      orphanRemoval = true)
  Set<AssessmentSelectionDetail> assessmentSelectionDetails;

  @Column(name = "weighting_pct")
  private BigDecimal weightingPercentage;

  @Embedded
  private Timestamps timestamps;
}
