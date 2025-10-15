package uk.gov.crowncommercial.dts.scale.cat.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.AssessmentEvaluationScore;

import java.util.List;
import java.util.Optional;

/**
 * Repository for AssessmentEvaluationScore entity operations
 */
@Repository
public interface AssessmentEvaluationScoreRepo extends JpaRepository<AssessmentEvaluationScore, Integer> {

  /**
   * Find all evaluation scores for a specific project and event
   */
  List<AssessmentEvaluationScore> findByProjectIdAndEventId(Integer projectId, Integer eventId);

  /**
   * Find evaluation scores for a specific assessor
   */
  List<AssessmentEvaluationScore> findByAssessorEmailId(String assessorEmailId);

  /**
   * Find evaluation scores for a specific project, event, and assessor
   */
  List<AssessmentEvaluationScore> findByProjectIdAndEventIdAndAssessorEmailId(
      Integer projectId, Integer eventId, String assessorEmailId);

  /**
   * Find evaluation score for a specific project, event, question, and assessor
   */
  Optional<AssessmentEvaluationScore> findByProjectIdAndEventIdAndQuestionIdAndAssessorEmailId(
      Integer projectId, Integer eventId, Integer questionId, String assessorEmailId);

  /**
   * Find evaluation scores for a specific question across all assessors
   */
  @Query("SELECT aes FROM AssessmentEvaluationScore aes WHERE aes.projectId = :projectId " +
         "AND aes.eventId = :eventId AND aes.questionId = :questionId")
  List<AssessmentEvaluationScore> findByProjectIdAndEventIdAndQuestionId(
      @Param("projectId") Integer projectId, 
      @Param("eventId") Integer eventId, 
      @Param("questionId") Integer questionId);
}


