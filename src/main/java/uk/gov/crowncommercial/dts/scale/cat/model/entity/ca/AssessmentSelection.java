package uk.gov.crowncommercial.dts.scale.cat.model.entity.ca;

import java.math.BigDecimal;
import javax.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.Timestamps;

/**
*
*/
@Entity
@Table(name = "assessment_selections")
@EqualsAndHashCode(exclude = "dimension")
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

  // @ManyToOne(fetch = FetchType.LAZY)
  // @JoinColumn(name = "assessment_id")
  // AssessmentEntity assessment;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "dimension_name")
  DimensionEntity dimension;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "requirement_taxon_id")
  RequirementTaxon requirementTaxon;

  @Column(name = "weighting_pct")
  private BigDecimal weightingPercentage;

  @Embedded
  private Timestamps timestamps;
}
