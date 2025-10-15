package uk.gov.crowncommercial.dts.scale.cat.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import uk.gov.crowncommercial.dts.scale.cat.interceptors.TrackExecutionTime;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.AssessmentEvaluationScoreRequest;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.AssessmentEvaluationScoreResponse;
import uk.gov.crowncommercial.dts.scale.cat.model.StringValueResponse;
import uk.gov.crowncommercial.dts.scale.cat.service.AssessmentEvaluationScoreService;

import java.util.List;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/**
 * Controller for managing assessment evaluation scores
 */
@RestController
@RequestMapping(path = "/tenders/assessment-evaluation-scores", produces = APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Slf4j
@Validated
public class AssessmentEvaluationScoreController extends AbstractRestController {

  private final AssessmentEvaluationScoreService assessmentEvaluationScoreService;

  /**
   * Create or update an assessment evaluation score
   */
  @PutMapping
  @TrackExecutionTime
  public ResponseEntity<AssessmentEvaluationScoreResponse> createOrUpdateEvaluationScore(
      @RequestBody @Valid final AssessmentEvaluationScoreRequest request,
      final JwtAuthenticationToken authentication) {

    var principal = getPrincipalFromJwt(authentication);
    log.info("createOrUpdateEvaluationScore invoked on behalf of principal: {}", principal);

    AssessmentEvaluationScoreResponse response = assessmentEvaluationScoreService
        .createOrUpdateEvaluationScore(request, principal);

    return ResponseEntity.status(HttpStatus.OK).body(response);
  }

  /**
   * Get all evaluation scores for a specific project and event
   */
  @GetMapping("/project/{project-id}/event/{event-id}")
  @TrackExecutionTime
  public ResponseEntity<List<AssessmentEvaluationScoreResponse>> getEvaluationScoresByProjectAndEvent(
      @PathVariable("project-id") final Integer projectId,
      @PathVariable("event-id") final Integer eventId,
      final JwtAuthenticationToken authentication) {

    var principal = getPrincipalFromJwt(authentication);
    log.info("getEvaluationScoresByProjectAndEvent invoked on behalf of principal: {} for project: {}, event: {}", 
        principal, projectId, eventId);

    List<AssessmentEvaluationScoreResponse> response = assessmentEvaluationScoreService
        .getEvaluationScoresByProjectAndEvent(projectId, eventId);

    return ResponseEntity.ok(response);
  }

  /**
   * Get evaluation scores for a specific assessor
   */
  @GetMapping("/assessor/{assessor-email}")
  @TrackExecutionTime
  public ResponseEntity<List<AssessmentEvaluationScoreResponse>> getEvaluationScoresByAssessor(
      @PathVariable("assessor-email") final String assessorEmail,
      final JwtAuthenticationToken authentication) {

    var principal = getPrincipalFromJwt(authentication);
    log.info("getEvaluationScoresByAssessor invoked on behalf of principal: {} for assessor: {}", 
        principal, assessorEmail);

    List<AssessmentEvaluationScoreResponse> response = assessmentEvaluationScoreService
        .getEvaluationScoresByAssessor(assessorEmail);

    return ResponseEntity.ok(response);
  }

  /**
   * Get evaluation scores for a specific question
   */
  @GetMapping("/project/{project-id}/event/{event-id}/question/{question-id}")
  @TrackExecutionTime
  public ResponseEntity<List<AssessmentEvaluationScoreResponse>> getEvaluationScoresByQuestion(
      @PathVariable("project-id") final Integer projectId,
      @PathVariable("event-id") final Integer eventId,
      @PathVariable("question-id") final Integer questionId,
      final JwtAuthenticationToken authentication) {

    var principal = getPrincipalFromJwt(authentication);
    log.info("getEvaluationScoresByQuestion invoked on behalf of principal: {} for project: {}, event: {}, question: {}", 
        principal, projectId, eventId, questionId);

    List<AssessmentEvaluationScoreResponse> response = assessmentEvaluationScoreService
        .getEvaluationScoresByQuestion(projectId, eventId, questionId);

    return ResponseEntity.ok(response);
  }

  /**
   * Get a specific evaluation score
   */
  @GetMapping("/project/{project-id}/event/{event-id}/question/{question-id}/assessor/{assessor-email}")
  @TrackExecutionTime
  public ResponseEntity<AssessmentEvaluationScoreResponse> getEvaluationScore(
      @PathVariable("project-id") final Integer projectId,
      @PathVariable("event-id") final Integer eventId,
      @PathVariable("question-id") final Integer questionId,
      @PathVariable("assessor-email") final String assessorEmail,
      final JwtAuthenticationToken authentication) {

    var principal = getPrincipalFromJwt(authentication);
    log.info("getEvaluationScore invoked on behalf of principal: {} for project: {}, event: {}, question: {}, assessor: {}", 
        principal, projectId, eventId, questionId, assessorEmail);

    AssessmentEvaluationScoreResponse response = assessmentEvaluationScoreService
        .getEvaluationScore(projectId, eventId, questionId, assessorEmail);

    return ResponseEntity.ok(response);
  }
}

