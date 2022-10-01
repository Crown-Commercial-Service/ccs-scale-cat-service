package uk.gov.crowncommercial.dts.scale.cat.controller;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import java.util.Collection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.model.DocumentKey;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.DocumentSummary;
import uk.gov.crowncommercial.dts.scale.cat.service.DocumentTemplateService;

/**
 *
 */
@RestController
@RequestMapping(path = "/tenders/projects/{procID}/events/{eventID}/documents/templates",
    produces = APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Slf4j
@Validated
public class DocumentTemplatesController extends AbstractRestController {

  private final DocumentTemplateService documentTemplateService;

  @GetMapping
  public Collection<DocumentSummary> getTemplates(@PathVariable("procID") final Integer procId,
      @PathVariable("eventID") final String eventId, final JwtAuthenticationToken authentication) {

    var principal = getPrincipalFromJwt(authentication);
    log.debug("getTemplates invoked on behalf of principal: {}", principal);
    return documentTemplateService.getTemplatesByAgreementAndLot(procId, eventId);
  }

  @GetMapping(value = "/{templateID}")
  public ResponseEntity<byte[]> getTemplate(@PathVariable("procID") final Integer procId,
      @PathVariable("eventID") final String eventId,
      @PathVariable("templateID") final String templateId,
      final JwtAuthenticationToken authentication) {

    var principal = getPrincipalFromJwt(authentication);
    log.debug("getTemplate invoked on behalf of principal: {}", principal);

    var documentKey = DocumentKey.fromString(templateId);
    var docAttachment = documentTemplateService.getTemplate(procId, eventId, documentKey);

    return ResponseEntity.ok().contentType(docAttachment.getContentType())
        .header(HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"" + documentKey.getFileName() + "\"")
        .body(docAttachment.getData());
  }

  @GetMapping(value = "/{templateID}/draft")
  public ResponseEntity<byte[]> getDraftProforma(@PathVariable("procID") final Integer procId,
      @PathVariable("eventID") final String eventId,
      @PathVariable("templateID") final String templateId,
      final JwtAuthenticationToken authentication) {

    var principal = getPrincipalFromJwt(authentication);
    log.debug("getDraftProforma invoked on behalf of principal: {}", principal);

    var documentKey = DocumentKey.fromString(templateId);
    var docAttachment = documentTemplateService.getDraftDocument(procId, eventId, documentKey);

    return ResponseEntity.ok().contentType(docAttachment.getContentType())
        .header(HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"" + docAttachment.getFileName() + "\"")
        .body(docAttachment.getData());
  }

}
