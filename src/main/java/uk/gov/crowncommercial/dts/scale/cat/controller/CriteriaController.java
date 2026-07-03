package uk.gov.crowncommercial.dts.scale.cat.controller;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import java.util.Set;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.interceptors.TrackExecutionTime;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.EvalCriteria;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.Question;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.QuestionGroup;
import uk.gov.crowncommercial.dts.scale.cat.service.CriteriaService;

/**
 *
 */
@RestController
@RequestMapping(path = "/tenders/projects/{proc-id}/events/{event-id}/criteria",
    produces = APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Slf4j
public class CriteriaController extends AbstractRestController {

  private final CriteriaService criteriaService;

  @GetMapping
  @TrackExecutionTime
  public Set<EvalCriteria> getEventEvaluationCriteria(
      @PathVariable("proc-id") final Integer procId,
      @PathVariable("event-id") final String eventId,
      final JwtAuthenticationToken authentication) {

    var principal = getPrincipalFromJwt(authentication);
    log.info("getEventEvaluationCriteria invoked on behalf of principal: {}", principal);

    return criteriaService.getEvalCriteria(procId, eventId, null, false);
  }

  @GetMapping("/stage/{stage-number}")
  @TrackExecutionTime
  public Set<EvalCriteria> getEventEvaluationCriteriaWithStageNumber(
      @PathVariable("proc-id") final Integer procId,
      @PathVariable("event-id") final String eventId,
      @PathVariable("stage-number") final Integer stageNumber,
      final JwtAuthenticationToken authentication) {

    var principal = getPrincipalFromJwt(authentication);
    log.info("getEventEvaluationCriteria invoked on behalf of principal: {}, stageNumber: {}", principal, stageNumber);

    return criteriaService.getEvalCriteria(procId, eventId, stageNumber, false);
  }

  @GetMapping("/{criterion-id}/groups")
  @TrackExecutionTime
  public Set<QuestionGroup> getEventEvaluationCriterionGroups(
      @PathVariable("proc-id") final Integer procId,
      @PathVariable("event-id") final String eventId,
      @PathVariable("criterion-id") final String criterionId,
      final JwtAuthenticationToken authentication) {

    var principal = getPrincipalFromJwt(authentication);
    log.info("getEventEvaluationCriterionGroups invoked on behalf of principal: {}", principal);

    return criteriaService.getEvalCriterionGroups(procId, eventId, criterionId, null, false);
  }

  @GetMapping("/{criterion-id}/groups/stage/{stage-number}")
  @TrackExecutionTime
  public Set<QuestionGroup> getEventEvaluationCriterionGroupsWithStageNumber(
      @PathVariable("proc-id") final Integer procId,
      @PathVariable("event-id") final String eventId,
      @PathVariable("criterion-id") final String criterionId,
      @PathVariable("stage-number") final Integer stageNumber,
      final JwtAuthenticationToken authentication) {

    var principal = getPrincipalFromJwt(authentication);
    log.info("getEventEvaluationCriterionGroups invoked on behalf of principal: {}, stageNumber: {}", principal, stageNumber);

    return criteriaService.getEvalCriterionGroups(procId, eventId, criterionId, stageNumber, false);
  }

  @GetMapping("/{criterion-id}/groups/{group-id}/questions")
  @TrackExecutionTime
  public Set<Question> getEventEvaluationCriterionGroupQuestions(
      @PathVariable("proc-id") final Integer procId,
      @PathVariable("event-id") final String eventId,
      @PathVariable("criterion-id") final String criterionId,
      @PathVariable("group-id") final String groupId,
      final JwtAuthenticationToken authentication) {

    var principal = getPrincipalFromJwt(authentication);
    log.info("getEventEvaluationCriterionGroupQuestions invoked on behalf of principal: {}",
        principal);

    return criteriaService.getEvalCriterionGroupQuestions(procId, eventId, criterionId, groupId, null);
  }

  @GetMapping("/{criterion-id}/groups/{group-id}/stage/{stage-number}/questions")
  @TrackExecutionTime
  public Set<Question> getEventEvaluationCriterionGroupQuestionsWithStageNumber(
      @PathVariable("proc-id") final Integer procId,
      @PathVariable("event-id") final String eventId,
      @PathVariable("criterion-id") final String criterionId,
      @PathVariable("group-id") final String groupId,
      @PathVariable("stage-number") final Integer stageNumber,
      final JwtAuthenticationToken authentication) {

    var principal = getPrincipalFromJwt(authentication);
    log.info("getEventEvaluationCriterionGroupQuestions invoked on behalf of principal: {}, stageNumber: {}", principal, stageNumber);

    return criteriaService.getEvalCriterionGroupQuestions(procId, eventId, criterionId, groupId, stageNumber);
  }

  @PutMapping("/{criterion-id}/groups/{group-id}/questions/{question-id}")
  @TrackExecutionTime
  public Question putQuestionOptionDetails(@RequestBody final Question question,
      @PathVariable("proc-id") final Integer procId,
      @PathVariable("event-id") final String eventId,
      @PathVariable("criterion-id") final String criterionId,
      @PathVariable("group-id") final String groupId,
      @PathVariable("question-id") final String questionId,
      final JwtAuthenticationToken authentication) {

    var principal = getPrincipalFromJwt(authentication);
    log.info("putQuestionOptionDetails invoked on behalf of principal: {}", principal);

    return criteriaService.putQuestionOptionDetails(question, procId, eventId, criterionId, groupId, questionId, null);
  }

  @PutMapping("/{criterion-id}/groups/{group-id}/questions/{question-id}/stage/{stage-number}")
  @TrackExecutionTime
  public Question putQuestionOptionDetailsWithStageNumber(@RequestBody final Question question,
      @PathVariable("proc-id") final Integer procId,
      @PathVariable("event-id") final String eventId,
      @PathVariable("criterion-id") final String criterionId,
      @PathVariable("group-id") final String groupId,
      @PathVariable("question-id") final String questionId,
      @PathVariable("stage-number") final Integer stageNumber,
      final JwtAuthenticationToken authentication) {

    var principal = getPrincipalFromJwt(authentication);
    log.info("putQuestionOptionDetails invoked on behalf of principal: {}, stageNumber: {}", principal, stageNumber);

    return criteriaService.putQuestionOptionDetails(question, procId, eventId, criterionId, groupId, questionId, stageNumber);
  }

  @PutMapping("/update-stage-descriptions")
  @TrackExecutionTime
  public void ensureStageDescriptionsAreStoredInProcurementStageEvent(
      @RequestBody final Object dummy,
      @PathVariable("proc-id") final Integer procId,
      @PathVariable("event-id") final String eventId,
      final JwtAuthenticationToken authentication) {

    var principal = getPrincipalFromJwt(authentication);
    log.info("ensureStageDescriptionsAreStoredInProcurementStageEvent invoked on behalf of principal: {}", principal);

    criteriaService.ensureStageDescriptionsAreStoredInProcurementStageEvent(eventId);
  }
}
