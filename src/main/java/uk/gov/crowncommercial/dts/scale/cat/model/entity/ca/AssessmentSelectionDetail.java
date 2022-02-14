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
@Table(name = "assessment_selection_details")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@EqualsAndHashCode(exclude = "assessmentSelection")
public class AssessmentSelectionDetail {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "assessment_selection_detail_id")
  Integer id;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "assessment_selection_id")
  AssessmentSelection assessmentSelection;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "assessment_submission_type_id")
  AssessmentToolSubmissionType assessmentToolSubmissionType;

  @Column(name = "requirement_value")
  private BigDecimal requirementValue;

  @Column(name = "requirement_valid_value_code")
  private String requirementValidValueCode;

  @Embedded
  private Timestamps timestamps;
}
