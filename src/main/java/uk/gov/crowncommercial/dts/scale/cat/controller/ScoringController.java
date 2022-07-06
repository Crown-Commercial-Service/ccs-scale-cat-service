package uk.gov.crowncommercial.dts.scale.cat.controller;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import java.util.Collection;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.model.DocumentKey;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.DocumentSummary;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.ScoreAndCommentNonOCDS;
import uk.gov.crowncommercial.dts.scale.cat.service.DocumentTemplateService;
import uk.gov.crowncommercial.dts.scale.cat.service.SupplierService;

@RestController
@RequestMapping(path = "/tenders/projects/{procId}/events/{eventId}/scores",
    produces = APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Slf4j
@Validated
public class ScoringController extends AbstractRestController {

  private final SupplierService supplierService;
  private final DocumentTemplateService documentTemplateService;
  private static final String EVENT_STAGE = "SCORING";

  @PutMapping
  public ResponseEntity<String> updateScoreAndComment(@PathVariable("procId") final Integer procId,
      @PathVariable("eventId") final String eventId,
      @RequestBody final List<ScoreAndCommentNonOCDS> scoresAndComments,
      final JwtAuthenticationToken authentication) {
    var principal = getPrincipalFromJwt(authentication);
    var conclaveOrgId = getCiiOrgIdFromJwt(authentication);
    log.info("update score and comments to suppliers invoked on behalf of principal: {}", principal,
        conclaveOrgId);
    return ResponseEntity.ok(supplierService.updateSupplierScoreAndComment(principal, procId,
        eventId, scoresAndComments));
  }
  
  @GetMapping
  public ResponseEntity<Collection<ScoreAndCommentNonOCDS>> getScores(
      @PathVariable("procId") final Integer procId, @PathVariable("eventId") final String eventId,
      final JwtAuthenticationToken authentication) {
    var principal = getPrincipalFromJwt(authentication);
    log.debug("getTemplates invoked on behalf of principal: {}", principal);
    return ResponseEntity.ok(supplierService.getScoresForSuppliers(procId, eventId));
  }

  @GetMapping("/templates")
  public Collection<DocumentSummary> getScoresTemplates(
      @PathVariable("procId") final Integer procId, @PathVariable("eventId") final String eventId,
      final JwtAuthenticationToken authentication) {

    var principal = getPrincipalFromJwt(authentication);
    log.debug("getTemplates invoked on behalf of principal: {}", principal);
    return documentTemplateService.getTemplatesByEventStage(procId, eventId, EVENT_STAGE);
  }

  @GetMapping(value = "/templates/{templateId}")
  public ResponseEntity<byte[]> getScoreTemplate(@PathVariable("procId") final Integer procId,
      @PathVariable("eventId") final String eventId,
      @PathVariable("templateId") final String templateId,
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
}
