package uk.gov.crowncommercial.dts.scale.cat.controller;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import java.util.Collection;
import java.util.List;
import javax.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.model.DocumentKey;
import uk.gov.crowncommercial.dts.scale.cat.model.StringValueResponse;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.*;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.MessageRequestInfo;
import uk.gov.crowncommercial.dts.scale.cat.service.DocGenService;
import uk.gov.crowncommercial.dts.scale.cat.service.ProcurementEventService;

/**
 *
 */
@RestController
@RequestMapping(path = "/tenders/projects/{procID}/events", produces = APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Slf4j
@Validated
public class EventsController extends AbstractRestController {

  private final ProcurementEventService procurementEventService;
  private final DocGenService docGenService;

  @GetMapping
  public List<EventSummary> getEventsForProject(@PathVariable("procID") final Integer procId,
      final JwtAuthenticationToken authentication) {

    var principal = getPrincipalFromJwt(authentication);
    log.info("getEventsForProject invoked on behalf of principal: {}", principal);

    return procurementEventService.getEventsForProject(procId);
  }

  @PostMapping
  public EventSummary createProcurementEvent(@PathVariable("procID") final Integer procId,
      @RequestBody final CreateEvent createEvent, final JwtAuthenticationToken authentication) {

    var principal = getPrincipalFromJwt(authentication);
    log.info("createProcuremenEvent invoked on behalf of principal: {}", principal);

    return procurementEventService.createEvent(procId, createEvent, null, principal);
  }

  @GetMapping("/{eventID}")
  public EventDetail getEvent(@PathVariable("procID") final Integer procId,
      @PathVariable("eventID") final String eventId, final JwtAuthenticationToken authentication) {

    var principal = getPrincipalFromJwt(authentication);
    log.info("getEvent invoked on behalf of principal: {}", principal);

    return procurementEventService.getEvent(procId, eventId);
  }

  @PutMapping("/{eventID}")
  public EventSummary updateProcurementEvent(@PathVariable("procID") final Integer procId,
      @PathVariable("eventID") final String eventId,
      @Valid @RequestBody final UpdateEvent updateEvent,
      final JwtAuthenticationToken authentication) {

    var principal = getPrincipalFromJwt(authentication);
    log.info("updateProcurementEvent invoked on behalf of principal: {}", principal);

    return procurementEventService.updateProcurementEvent(procId, eventId, updateEvent, principal);
  }

  @GetMapping("/{eventID}/suppliers")
  public Collection<OrganizationReference> getSuppliers(
      @PathVariable("procID") final Integer procId, @PathVariable("eventID") final String eventId,
      final JwtAuthenticationToken authentication) {

    var principal = getPrincipalFromJwt(authentication);
    log.info("getSuppliers invoked on behalf of principal: {}", principal);

    return procurementEventService.getSuppliers(procId, eventId);
  }

  @PostMapping("/{eventID}/suppliers")
  public OrganizationReference addSupplier(@PathVariable("procID") final Integer procId,
      @PathVariable("eventID") final String eventId,
      @RequestBody final OrganizationReference organizationReference,
      final JwtAuthenticationToken authentication) {

    var principal = getPrincipalFromJwt(authentication);
    log.info("getSuppliers invoked on behalf of principal: {}", principal);

    return procurementEventService.addSupplier(procId, eventId, organizationReference);
  }

  @DeleteMapping("/{eventID}/suppliers/{supplierID}")
  public String deleteSupplier(@PathVariable("procID") final Integer procId,
      @PathVariable("eventID") final String eventId,
      @PathVariable("supplierID") final String supplierId,
      final JwtAuthenticationToken authentication) {

    var principal = getPrincipalFromJwt(authentication);
    log.info("deleteSupplier invoked on behalf of principal: {}", principal);

    procurementEventService.deleteSupplier(procId, eventId, supplierId);

    return "OK";
  }

  @GetMapping("/{eventID}/documents")
  public Collection<DocumentSummary> getDocumentSummaries(
      @PathVariable("procID") final Integer procId, @PathVariable("eventID") final String eventId,
      final JwtAuthenticationToken authentication) {

    var principal = getPrincipalFromJwt(authentication);
    log.info("getDocumentSummaries invoked on behalf of principal: {}", principal);

    return procurementEventService.getDocumentSummaries(procId, eventId);
  }

  @PutMapping("/{eventID}/documents")
  public DocumentSummary uploadDocument(@PathVariable("procID") final Integer procId,
      @PathVariable("eventID") final String eventId,
      @RequestParam("data") final MultipartFile multipartFile,
      @RequestParam("audience") final String audience,
      @RequestParam(value = "description", required = false) final String description,
      final JwtAuthenticationToken authentication) {

    var principal = getPrincipalFromJwt(authentication);
    log.info("uploadDocument invoked on behalf of principal: {}", principal);

    // passed in as string to allow for lower case
    var audienceType = DocumentAudienceType.valueOf(audience.toUpperCase());
    return procurementEventService.uploadDocument(procId, eventId, multipartFile, audienceType,
        description);
  }

  @GetMapping(value = "/{eventID}/documents/{documentID}")
  public ResponseEntity<byte[]> getDocument(@PathVariable("procID") final Integer procId,
      @PathVariable("eventID") final String eventId,
      @PathVariable("documentID") final String documentId,
      final JwtAuthenticationToken authentication) {

    var principal = getPrincipalFromJwt(authentication);
    log.info("getDocument invoked on behalf of principal: {}", principal);

    var document = procurementEventService.getDocument(procId, eventId, documentId);
    var documentKey = DocumentKey.fromString(documentId);

    return ResponseEntity.ok().contentType(document.getContentType())
        .header(HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"" + documentKey.getFileName() + "\"")
        .body(document.getData());
  }

  @PutMapping("/{eventID}/publish")
  public StringValueResponse publishEvent(@PathVariable("procID") final Integer procId,
      @PathVariable("eventID") final String eventId,
      @RequestBody @Valid final PublishDates publishDates,
      final JwtAuthenticationToken authentication) {

    var principal = getPrincipalFromJwt(authentication);
    log.info("publishEvent invoked on behalf of principal: {}", principal);

    docGenService.generateProformaDocument(procId, eventId);
    procurementEventService.publishEvent(procId, eventId, publishDates, principal);

    return new StringValueResponse("OK");
  }

  @GetMapping("/{eventID}/messages")
  public MessageSummary getMessages(
          @PathVariable("procID") final Integer procId,
          @PathVariable("eventID") final String eventId,
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
    return procurementEventService.getMessagesSummary(messageRequestInfo);
  }

  @GetMapping("/{eventID}/messages/{messageId}")
  public uk.gov.crowncommercial.dts.scale.cat.model.generated.Message getMessage(
          @PathVariable("procID") final Integer procId,
          @PathVariable("eventID") final String eventId,
          @PathVariable("messageId") final String messageId,
          final JwtAuthenticationToken authentication) {

    var principal = getPrincipalFromJwt(authentication);
    log.info("getMessagesSummaries invoked on behalf of principal: {}", principal);

    return procurementEventService.getMessageSummary(procId, eventId,messageId, principal);
  }
}
