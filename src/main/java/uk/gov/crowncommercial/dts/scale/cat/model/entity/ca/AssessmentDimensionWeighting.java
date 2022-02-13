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
@Table(name = "assessment_dimension_weighting")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AssessmentDimensionWeighting {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "assessment_dimension_weighting_id")
  Integer id;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "dimension_id")
  DimensionEntity dimension;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "assessment_id")
  AssessmentEntity assessment;

  @EqualsAndHashCode.Exclude
  @ManyToMany(fetch = FetchType.EAGER)
  @JoinTable(name = "assessment_dimension_submission_types",
      joinColumns = @JoinColumn(name = "assessment_dimension_weighting_id"),
      inverseJoinColumns = @JoinColumn(name = "assessment_submission_type_id"))
  Set<AssessmentToolSubmissionType> assessmentToolSubmissionTypes;

  @Column(name = "weighting_pct")
  private BigDecimal weightingPercentage;

  @Embedded
  private Timestamps timestamps;
}
