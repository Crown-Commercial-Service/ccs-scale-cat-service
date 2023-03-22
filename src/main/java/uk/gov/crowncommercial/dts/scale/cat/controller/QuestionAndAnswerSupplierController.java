package uk.gov.crowncommercial.dts.scale.cat.controller;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.interceptors.TrackExecutionTime;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.QandAWithProjectDetails;
import uk.gov.crowncommercial.dts.scale.cat.service.QuestionAndAnswerService;

/**
 * Q&A Controller which create question and answers to event
 *
 */
@RestController
@RequestMapping(path = "/tenders/supplier/{supplier-email}/projects/{proc-id}/events/{event-id}/q-and-a",
    produces = APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Slf4j
@Validated
public class QuestionAndAnswerSupplierController extends AbstractRestController {

  private final QuestionAndAnswerService questionAndAnswerService;

  @GetMapping
  @TrackExecutionTime
  public ResponseEntity<QandAWithProjectDetails> getQuestionAdnAnswers(
      @PathVariable("supplier-email") final String supplierEmail,
      @PathVariable("proc-id") final Integer procId,
      @PathVariable("event-id") final String eventId) {
    log.info("getQuestionAdnAnswersForSupplier invoked on behalf of principal: {}", supplierEmail);
    return ResponseEntity
        .ok(questionAndAnswerService.getQuestionAndAnswerForSupplierByEvent(procId, eventId, supplierEmail));
  }
}
