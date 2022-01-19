package uk.gov.crowncommercial.dts.scale.cat.model.entity.ca;

import javax.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.Timestamps;

/**
*
*/
@Entity
@Table(name = "assessment_submission_types")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AssessmentSubmissionType {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "assessment_submission_type_id")
  Integer id;

  @Column(name = "assessment_tool_id")
  private Integer toolId;

  @Column(name = "submission_type_code")
  private String code;

  @Embedded
  private Timestamps timestamps;
}
