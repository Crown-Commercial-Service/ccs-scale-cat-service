package uk.gov.crowncommercial.dts.scale.cat.model.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 * Entity representing assessment evaluation scores
 */
@Entity
@Table(name = "assessment_evaluation_score")
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

  @Column(name = "lot")
  String lot;

  @Column(name = "question_id", nullable = false)
  String questionId;

  @Column(name = "question_text")
  String questionText;

  @Column(name = "assessor_email", nullable = false)
  String assessorEmail;

  @Column(name = "assessor_score")
  Integer assessorScore;

  @Column(name = "assessor_comment", columnDefinition = "TEXT")
  String assessorComment;

  @Column(name = "created_at")
  java.time.Instant createdAt;

  @Column(name = "updated_at")
  java.time.Instant updatedAt;
}
