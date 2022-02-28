package uk.gov.crowncommercial.dts.scale.cat.controller;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import javax.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.Message;
import uk.gov.crowncommercial.dts.scale.cat.service.MessageService;

/**
 * Message Controller which provides outbound messages API to jaggaer
 *
 */
@RestController
@RequestMapping(path = "/tenders/projects", produces = APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Slf4j
@Validated
public class MessageController extends AbstractRestController {

  private final MessageService messageService;

  @PostMapping("{proc-id}/events/{event-id}/messages")
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

}
