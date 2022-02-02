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
@Table(name = "assessment_dimension_weighting")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@EqualsAndHashCode(exclude = "assessment")
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

  @Column(name = "weighting_pct")
  private BigDecimal weightingPercentage;

  @Embedded
  private Timestamps timestamps;
}
