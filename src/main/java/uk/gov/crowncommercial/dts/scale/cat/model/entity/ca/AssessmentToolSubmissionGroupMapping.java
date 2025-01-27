package uk.gov.crowncommercial.dts.scale.cat.model.entity.ca;

import lombok.*;
import lombok.experimental.FieldDefaults;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.Timestamps;

import jakarta.persistence.*;

/**
*
*/
@Entity
@Table(name = "assessment_tool_submission_group")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AssessmentToolSubmissionGroupMapping {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "assessment_tool_submission_group_id")
  Integer id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "submission_group_id")
  SubmissionGroup submissionGroup;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "assessment_tool_id")
  AssessmentTool assessmentTool;

  @Embedded
  private Timestamps timestamps;
}
