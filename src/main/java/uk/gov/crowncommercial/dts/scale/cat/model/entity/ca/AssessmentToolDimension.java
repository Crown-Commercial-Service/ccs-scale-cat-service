package uk.gov.crowncommercial.dts.scale.cat.model.entity.ca;

import lombok.*;
import lombok.experimental.FieldDefaults;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.Timestamps;

import javax.persistence.*;
import java.math.BigDecimal;
import java.util.Set;

/**
*
*/
@Entity
@Table(name = "assessment_tool_dimensions")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AssessmentToolDimension {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "assessment_tool_dimension_id")
  private Integer id;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "dimension_id")
  private DimensionEntity dimension;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "assessment_tool_id")
  private AssessmentTool assessmentTool;

  @Column(name = "min_weighting_pct")
  private BigDecimal minWeightingPercentage;

  @Column(name = "max_weighting_pct")
  private BigDecimal maxWeightingPercentage;

  @Column(name = "calculation_rule_id")
  private Integer calculationRuleId;

  @Column(name = "exclusion_policy_id")
  private Integer exclusionPolicyId;

  @Embedded
  private Timestamps timestamps;

}
