package uk.gov.crowncommercial.dts.scale.cat.controller;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import javax.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.*;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.MessageRequestInfo;
import uk.gov.crowncommercial.dts.scale.cat.service.MessageService;

/**
 * Message Controller which provides outbound messages API to jaggaer
 *
 */
@RestController
@RequestMapping(path = "/tenders/projects/{proc-id}/events/{event-id}/messages", produces = APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Slf4j
@Validated
public class MessageController extends AbstractRestController {

  private final MessageService messageService;

  @PostMapping
  public ResponseEntity<String> createAndRespondMessage(
      @PathVariable("proc-id") final Integer procId, @PathVariable("event-id") final String eventId,
      @Valid @RequestBody final Message messageRequest,
      final JwtAuthenticationToken authentication) {

    var principal = getPrincipalFromJwt(authentication);
    var conclaveOrgId = getCiiOrgIdFromJwt(authentication);
    log.info("createAndRespondMessage invoked on behalf of principal: {}", principal,
        conclaveOrgId);

    return ResponseEntity
        .ok(messageService.sendOrRespondMessage(principal, procId, eventId, messageRequest));
  }
  @GetMapping
  public MessageSummary getMessages(
          @PathVariable("proc-id") final Integer procId,
          @PathVariable("event-id") final String eventId,
          @RequestParam(name = "message-direction",required = false, defaultValue = "RECEIVED") MessageDirection messageDirection,
          @RequestParam(name = "message-read",required = false, defaultValue = "ALL") MessageRead messageRead,
          @RequestParam(name = "sort",required = false, defaultValue = "DATE") MessageSort messageSort,
          @RequestParam(name = "sort-order",required = false , defaultValue = "ASCENDING") MessageSortOrder messageSortOrder,
          @RequestParam(name = "page",required = false, defaultValue = "1") Integer page,
          @RequestParam(name = "page-size",required = false, defaultValue = "20") Integer pageSize,
          final JwtAuthenticationToken authentication) {

    var principal = getPrincipalFromJwt(authentication);
    log.info("getMessagesSummaries invoked on behalf of principal: {}", principal);

    var  messageRequestInfo = MessageRequestInfo.builder()
            .procId(procId)
            .eventId(eventId)
            .messageDirection(messageDirection)
            .messageRead(messageRead)
            .messageSort(messageSort)
            .messageSortOrder(messageSortOrder)
            .page(page)
            .pageSize(pageSize)
            .principal(principal)
            .build();
    return messageService.getMessagesSummary(messageRequestInfo);
  }

  @GetMapping("/{message-id}")
  public Message getMessage(
          @PathVariable("proc-id") final Integer procId,
          @PathVariable("event-id") final String eventId,
          @PathVariable("message-id") final String messageId,
          final JwtAuthenticationToken authentication) {

    var principal = getPrincipalFromJwt(authentication);
    log.info("getMessagesSummaries invoked on behalf of principal: {}", principal);

    return messageService.getMessageSummary(procId, eventId,messageId, principal);
  }
}
