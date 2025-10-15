package uk.gov.crowncommercial.dts.scale.cat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.crowncommercial.dts.scale.cat.exception.ResourceNotFoundException;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.AssessmentEvaluationScoreRequest;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.AssessmentEvaluationScoreResponse;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.AssessmentEvaluationScore;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.Timestamps;
import uk.gov.crowncommercial.dts.scale.cat.repo.AssessmentEvaluationScoreRepo;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static uk.gov.crowncommercial.dts.scale.cat.model.entity.Timestamps.createTimestamps;
import static uk.gov.crowncommercial.dts.scale.cat.model.entity.Timestamps.updateTimestamps;

/**
 * Service for managing assessment evaluation scores
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AssessmentEvaluationScoreService {

  private final AssessmentEvaluationScoreRepo assessmentEvaluationScoreRepo;

  private static final String ERR_MSG_FMT_EVALUATION_SCORE_NOT_FOUND = 
      "Assessment evaluation score not found for project [%d], event [%d], question [%d], assessor [%s]";

  /**
   * Create or update an assessment evaluation score
   */
  @Transactional
  public AssessmentEvaluationScoreResponse createOrUpdateEvaluationScore(
      AssessmentEvaluationScoreRequest request, String principal) {
    
    log.info("Creating or updating evaluation score for project [{}], event [{}], question [{}], assessor [{}]", 
        request.getProjectId(), request.getEventId(), request.getQuestionId(), request.getAssessorEmailId());

    Optional<AssessmentEvaluationScore> existingScore = assessmentEvaluationScoreRepo
        .findByProjectIdAndEventIdAndQuestionIdAndAssessorEmailId(
            request.getProjectId(), 
            request.getEventId(), 
            request.getQuestionId(), 
            request.getAssessorEmailId());

    AssessmentEvaluationScore score;
    
    if (existingScore.isPresent()) {
      // Update existing score
      AssessmentEvaluationScore existing = existingScore.get();
      existing.setAssessorScore(request.getAssessorScore());
      existing.setAssessorComment(request.getAssessorComment());
      existing.setTimestamps(updateTimestamps(existing.getTimestamps(), principal));
      score = existing;
      log.info("Updating existing evaluation score with ID [{}]", existing.getId());
    } else {
      // Create new score
      score = AssessmentEvaluationScore.builder()
          .projectId(request.getProjectId())
          .eventId(request.getEventId())
          .frameworkAgreement(request.getFrameworkAgreement())
          .questionId(request.getQuestionId())
          .assessorEmailId(request.getAssessorEmailId())
          .assessorScore(request.getAssessorScore())
          .assessorComment(request.getAssessorComment())
          .timestamps(createTimestamps(principal))
          .build();
      log.info("Creating new evaluation score");
    }

    AssessmentEvaluationScore savedScore = assessmentEvaluationScoreRepo.save(score);
    log.info("Successfully saved evaluation score with ID [{}]", savedScore.getId());
    
    return mapToResponse(savedScore);
  }

  /**
   * Get all evaluation scores for a specific project and event
   */
  public List<AssessmentEvaluationScoreResponse> getEvaluationScoresByProjectAndEvent(
      Integer projectId, Integer eventId) {
    
    log.info("Retrieving evaluation scores for project [{}] and event [{}]", projectId, eventId);
    
    List<AssessmentEvaluationScore> scores = assessmentEvaluationScoreRepo
        .findByProjectIdAndEventId(projectId, eventId);
    
    log.info("Found [{}] evaluation scores for project [{}] and event [{}]", 
        scores.size(), projectId, eventId);
    
    return scores.stream()
        .map(this::mapToResponse)
        .collect(Collectors.toList());
  }

  /**
   * Get evaluation scores for a specific assessor
   */
  public List<AssessmentEvaluationScoreResponse> getEvaluationScoresByAssessor(String assessorEmailId) {
    
    log.info("Retrieving evaluation scores for assessor [{}]", assessorEmailId);
    
    List<AssessmentEvaluationScore> scores = assessmentEvaluationScoreRepo
        .findByAssessorEmailId(assessorEmailId);
    
    log.info("Found [{}] evaluation scores for assessor [{}]", scores.size(), assessorEmailId);
    
    return scores.stream()
        .map(this::mapToResponse)
        .collect(Collectors.toList());
  }

  /**
   * Get evaluation scores for a specific project, event, and assessor
   */
  public List<AssessmentEvaluationScoreResponse> getEvaluationScoresByProjectEventAndAssessor(
      Integer projectId, Integer eventId, String assessorEmailId) {
    
    log.info("Retrieving evaluation scores for project [{}], event [{}], assessor [{}]", 
        projectId, eventId, assessorEmailId);
    
    List<AssessmentEvaluationScore> scores = assessmentEvaluationScoreRepo
        .findByProjectIdAndEventIdAndAssessorEmailId(projectId, eventId, assessorEmailId);
    
    log.info("Found [{}] evaluation scores for project [{}], event [{}], assessor [{}]", 
        scores.size(), projectId, eventId, assessorEmailId);
    
    return scores.stream()
        .map(this::mapToResponse)
        .collect(Collectors.toList());
  }

  /**
   * Get evaluation scores for a specific question
   */
  public List<AssessmentEvaluationScoreResponse> getEvaluationScoresByQuestion(
      Integer projectId, Integer eventId, Integer questionId) {
    
    log.info("Retrieving evaluation scores for project [{}], event [{}], question [{}]", 
        projectId, eventId, questionId);
    
    List<AssessmentEvaluationScore> scores = assessmentEvaluationScoreRepo
        .findByProjectIdAndEventIdAndQuestionId(projectId, eventId, questionId);
    
    log.info("Found [{}] evaluation scores for project [{}], event [{}], question [{}]", 
        scores.size(), projectId, eventId, questionId);
    
    return scores.stream()
        .map(this::mapToResponse)
        .collect(Collectors.toList());
  }

  /**
   * Get a specific evaluation score
   */
  public AssessmentEvaluationScoreResponse getEvaluationScore(
      Integer projectId, Integer eventId, Integer questionId, String assessorEmailId) {
    
    log.info("Retrieving evaluation score for project [{}], event [{}], question [{}], assessor [{}]", 
        projectId, eventId, questionId, assessorEmailId);
    
    Optional<AssessmentEvaluationScore> score = assessmentEvaluationScoreRepo
        .findByProjectIdAndEventIdAndQuestionIdAndAssessorEmailId(
            projectId, eventId, questionId, assessorEmailId);
    
    if (score.isEmpty()) {
      throw new ResourceNotFoundException(
          String.format(ERR_MSG_FMT_EVALUATION_SCORE_NOT_FOUND, 
              projectId, eventId, questionId, assessorEmailId));
    }
    
    return mapToResponse(score.get());
  }

  /**
   * Map entity to response DTO
   */
  private AssessmentEvaluationScoreResponse mapToResponse(AssessmentEvaluationScore score) {
    AssessmentEvaluationScoreResponse response = new AssessmentEvaluationScoreResponse();
    response.setId(score.getId());
    response.setProjectId(score.getProjectId());
    response.setEventId(score.getEventId());
    response.setFrameworkAgreement(score.getFrameworkAgreement());
    response.setQuestionId(score.getQuestionId());
    response.setAssessorEmailId(score.getAssessorEmailId());
    response.setAssessorScore(score.getAssessorScore());
    response.setAssessorComment(score.getAssessorComment());
    
    // Convert Instant to OffsetDateTime
    if (score.getTimestamps() != null) {
      if (score.getTimestamps().getCreatedAt() != null) {
        response.setCreatedAt(score.getTimestamps().getCreatedAt().atOffset(java.time.ZoneOffset.UTC));
      }
      if (score.getTimestamps().getUpdatedAt() != null) {
        response.setUpdatedAt(score.getTimestamps().getUpdatedAt().atOffset(java.time.ZoneOffset.UTC));
      }
    }
    
    return response;
  }
}
