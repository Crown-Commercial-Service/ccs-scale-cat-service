package uk.gov.crowncommercial.dts.scale.cat.model.entity.ca;

import lombok.*;
import lombok.experimental.FieldDefaults;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.Timestamps;

import jakarta.persistence.*;

/**
*
*/
@Entity
@Table(name = "assessment_dimension_criteria")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@EqualsAndHashCode(exclude = {"assessment", "dimension"})
public class AssessmentDimensionCriteria {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "assessment_dimension_criteria_id")
  Integer id;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "assessment_id")
  AssessmentEntity assessment;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "dimension_id")
  DimensionEntity dimension;


  @Column(name = "criterion_id")
  Integer criterionId;

  @Column(name = "active")
  Boolean active;

  @Embedded
  private Timestamps timestamps;
}
