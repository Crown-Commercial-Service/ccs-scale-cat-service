package uk.gov.crowncommercial.dts.scale.cat.model.entity.ca;

import javax.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.Timestamps;

/**
*
*/
@Entity
@Table(name = "assessment_selection_results")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AssessmentSelectionResult {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "assessment_selection_result_id")
  Integer id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "assessment_selection_id")
  AssessmentSelection assessmentSelection;

  @Column(name = "assessment_result_value")
  private Double assessmentResultValue;

  @Column(name = "assessment_selection_result_reference")
  private Integer assessmentSelectionResultReference;

  @Embedded
  private Timestamps timestamps;
}
