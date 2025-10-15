package uk.gov.crowncommercial.dts.scale.cat.model.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 * Entity representing assessment evaluation scores
 */
@Entity
@Table(name = "assessment_evaluation_scores")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AssessmentEvaluationScore {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  Integer id;

  @Column(name = "project_id", nullable = false)
  Integer projectId;

  @Column(name = "event_id", nullable = false)
  Integer eventId;

  @Column(name = "framework_agreement")
  String frameworkAgreement;

  @Column(name = "question_id", nullable = false)
  Integer questionId;

  @Column(name = "assessor_email_id", nullable = false)
  String assessorEmailId;

  @Column(name = "assessor_score")
  Integer assessorScore;

  @Column(name = "assessor_comment", columnDefinition = "TEXT")
  String assessorComment;

  @Embedded
  private Timestamps timestamps;
}
