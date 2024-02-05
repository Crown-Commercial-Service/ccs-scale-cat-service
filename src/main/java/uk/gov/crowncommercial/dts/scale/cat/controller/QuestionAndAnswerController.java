package uk.gov.crowncommercial.dts.scale.cat.controller;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.interceptors.TrackExecutionTime;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.QandA;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.QandAWithProjectDetails;
import uk.gov.crowncommercial.dts.scale.cat.service.QuestionAndAnswerService;
import uk.gov.crowncommercial.dts.scale.cat.utils.SanitisationUtils;

/**
 * Q&A Controller which create question and answers to event
 *
 */
@RestController
@RequestMapping(path = "/tenders/projects/{proc-id}/events/{event-id}/q-and-a",
    produces = APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Slf4j
@Validated
public class QuestionAndAnswerController extends AbstractRestController {

  private final QuestionAndAnswerService questionAndAnswerService;
  private final SanitisationUtils sanitisationUtils;

  @PostMapping
  @TrackExecutionTime
  public ResponseEntity<QandA> createQuestionAndAnswer(
      @PathVariable("proc-id") final Integer procId, @PathVariable("event-id") final String eventId,
      @Valid @RequestBody final QandA questionRequest,
      final JwtAuthenticationToken authentication) {

    var principal = getPrincipalFromJwt(authentication);
    var conclaveOrgId = getCiiOrgIdFromJwt(authentication);
    log.info("createQuestionAndAnswer invoked on behalf of principal: {}", principal,
        conclaveOrgId);

    questionRequest.setQuestion(sanitisationUtils.sanitiseStringAsFormattedText(questionRequest.getQuestion()));
    questionRequest.setAnswer(sanitisationUtils.sanitiseStringAsFormattedText(questionRequest.getAnswer()));

    return ResponseEntity.ok(questionAndAnswerService.createOrUpdateQuestionAndAnswer(principal,
        procId, eventId, questionRequest, null));
  }

  @PutMapping("/{qAndA-id}")
  @TrackExecutionTime
  public ResponseEntity<QandA> updateQuestionAndAnswer(
      @PathVariable("proc-id") final Integer procId, @PathVariable("event-id") final String eventId,
      @Valid @RequestBody final QandA questionRequest,
      @PathVariable("qAndA-id") final Integer questionId,
      final JwtAuthenticationToken authentication) {

    var principal = getPrincipalFromJwt(authentication);
    var conclaveOrgId = getCiiOrgIdFromJwt(authentication);
    log.info("updateQuestionAndAnswer invoked on behalf of principal: {}", principal,
        conclaveOrgId);

    questionRequest.setQuestion(sanitisationUtils.sanitiseStringAsFormattedText(questionRequest.getQuestion()));
    questionRequest.setAnswer(sanitisationUtils.sanitiseStringAsFormattedText(questionRequest.getAnswer()));

    return ResponseEntity.ok(questionAndAnswerService.createOrUpdateQuestionAndAnswer(principal,
        procId, eventId, questionRequest, questionId));
  }

  @GetMapping
  @TrackExecutionTime
  public ResponseEntity<QandAWithProjectDetails> getQuestionAdnAnswers(
      @PathVariable("proc-id") final Integer procId, @PathVariable("event-id") final String eventId,
      final JwtAuthenticationToken authentication) {
    var principal = getPrincipalFromJwt(authentication);
    log.info("getQuestionAdnAnswers invoked on behalf of principal: {}", principal);
    return ResponseEntity.ok(questionAndAnswerService.getQuestionAndAnswerByEvent(procId, eventId, principal));
  }
}
