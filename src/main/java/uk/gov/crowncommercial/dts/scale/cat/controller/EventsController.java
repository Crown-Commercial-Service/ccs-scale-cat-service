package uk.gov.crowncommercial.dts.scale.cat.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.StopWatch;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import uk.gov.crowncommercial.dts.scale.cat.exception.NotSupportedException;
import uk.gov.crowncommercial.dts.scale.cat.interceptors.TrackExecutionTime;
import uk.gov.crowncommercial.dts.scale.cat.model.*;
import uk.gov.crowncommercial.dts.scale.cat.model.assessment.SupplierScore;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.*;
import uk.gov.crowncommercial.dts.scale.cat.service.*;
import uk.gov.crowncommercial.dts.scale.cat.service.ca.AssessmentScoreExportService;

import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static uk.gov.crowncommercial.dts.scale.cat.config.Constants.TENDER_DB_ONLY_EVENT_TYPES;

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
  private final AssessmentScoreExportService scoreExportService;

  private final EventTransitionService eventTransitionService;
  private final DocGenService docGenService;
  private static final String EXPORT_BUYER_DOCUMENTS_NAME = "buyer_attachments";

  private static final String EXPORT_SUPPLIER_RESPONSE_DOCUMENTS_NAME = "responses_%s";
  private static final String EXPORT_SINGLE_SUPPLIER_RESPONSE_DOCUMENTS_NAME = "response_%s_%s";

  @GetMapping
  @TrackExecutionTime
  public List<EventSummary> getEventsForProject(@PathVariable("procID") final Integer procId,
      final JwtAuthenticationToken authentication) {

    var principal = getPrincipalFromJwt(authentication);
    log.info("getEventsForProject invoked on behalf of principal: {}", principal);

    return procurementEventService.getEventsForProject(procId, principal);
  }

  @PostMapping
  @TrackExecutionTime
  public EventSummary createProcurementEvent(@PathVariable("procID") final Integer procId, @RequestBody final CreateEvent createEvent, final String isGcloudEventStr, final JwtAuthenticationToken authentication) {

    var principal = getPrincipalFromJwt(authentication);
    log.info("createProcuremenEvent invoked on behalf of principal: {}", principal);


    if(null != createEvent.getNonOCDS() && null != createEvent.getNonOCDS().getEventType()) {
      DefineEventType eventType = createEvent.getNonOCDS().getEventType();
     String eventTypeValue = createEvent.getNonOCDS().getEventType().getValue();
      if(TENDER_DB_ONLY_EVENT_TYPES.contains(ViewEventType.fromValue(eventTypeValue))){
        createEvent.getNonOCDS().setEventType(null);
        EventSummary summary = procurementEventService.createEvent(procId, createEvent, null, principal);
        String eventId = summary.getId();
        UpdateEvent event = new UpdateEvent();
        event.setEventType(eventType);
        EventSummary updSummary = procurementEventService.updateProcurementEvent(procId, eventId, event, principal);
        summary.setEventType(updSummary.getEventType());
        summary.setAssessmentId(updSummary.getAssessmentId());
        return summary;
      }
    }

    return procurementEventService.createEvent(procId, createEvent, null, principal);
  }

  @GetMapping("/{eventID}")
  @TrackExecutionTime
  public EventDetail getEvent(@PathVariable("procID") final Integer procId,
      @PathVariable("eventID") final String eventId, final JwtAuthenticationToken authentication) {

    var principal = getPrincipalFromJwt(authentication);
    log.info("getEvent invoked on behalf of principal: {}", principal);

    return procurementEventService.getEvent(procId, eventId);
  }

  @PutMapping("/{eventID}")
  @TrackExecutionTime
  public EventSummary updateProcurementEvent(@PathVariable("procID") final Integer procId,
      @PathVariable("eventID") final String eventId,
      @Valid @RequestBody final UpdateEvent updateEvent,
      final JwtAuthenticationToken authentication) {

    var principal = getPrincipalFromJwt(authentication);
    log.info("updateProcurementEvent invoked on behalf of principal: {}", principal);

    return procurementEventService.updateProcurementEvent(procId, eventId, updateEvent, principal);
  }

  @GetMapping("/{eventID}/suppliers")
  @TrackExecutionTime
  public EventSuppliers getSuppliers(
      @PathVariable("procID") final Integer procId, @PathVariable("eventID") final String eventId,
      final JwtAuthenticationToken authentication) {

    var principal = getPrincipalFromJwt(authentication);
    log.info("getSuppliers invoked on behalf of principal: {}", principal);

    return procurementEventService.getSuppliers(procId, eventId);
  }

  @GetMapping("/{eventID}/scores/export")
  @TrackExecutionTime
  public ResponseEntity<InputStreamResource> exportScroes(
          @PathVariable("procID") final Integer procId, @PathVariable("eventID") final String eventId,
          final JwtAuthenticationToken authentication,
          @RequestParam(name="maxScore", required = false) Float maxScore,
          @RequestParam(name="minScore", required = false) Float minScore,
          @RequestHeader(name = "mime-type", required = false,
                  defaultValue = "text/csv") final String mimeType) {

    var principal = getPrincipalFromJwt(authentication);
    log.info("getSuppliers invoked on behalf of principal: {}", principal);

    List<SupplierScore> scores = scoreExportService.getScores(procId, eventId, minScore, maxScore, Optional.of(principal));
    return scoreExportService.export(procId, eventId, scores, mimeType);
  }

  @GetMapping("/{eventID}/suppliers/{supplierID}")
  @TrackExecutionTime
  public OrganizationReference1 getSupplierInfo(
          @PathVariable("procID") final Integer procId, @PathVariable("eventID") final String eventId,
          @PathVariable("supplierID") final String suppplierId,
          final JwtAuthenticationToken authentication) {

    var principal = getPrincipalFromJwt(authentication);
    log.info("getSupplierInfo invoked on behalf of principal: {}", principal);

    return procurementEventService.getSupplierInfo(procId, eventId,suppplierId);
  }

  @PostMapping("/{eventID}/suppliers")
  @TrackExecutionTime
  public EventSuppliers addSupplier(@PathVariable("procID") final Integer procId,
      @PathVariable("eventID") final String eventId,
      @RequestBody final EventSuppliers eventSuppliers,
      final JwtAuthenticationToken authentication) {

    var principal = getPrincipalFromJwt(authentication);
    log.info("getSuppliers invoked on behalf of principal: {}", principal);

    return procurementEventService.addSuppliers(procId, eventId, eventSuppliers,
        eventSuppliers.getOverwriteSuppliers() == null ?
            false :
            eventSuppliers.getOverwriteSuppliers(), principal);
  }

  @DeleteMapping("/{eventID}/suppliers/{supplierID}")
  @TrackExecutionTime
  public String deleteSupplier(@PathVariable("procID") final Integer procId,
      @PathVariable("eventID") final String eventId,
      @PathVariable("supplierID") final String supplierId,
      final JwtAuthenticationToken authentication) {

    var principal = getPrincipalFromJwt(authentication);
    log.info("deleteSupplier invoked on behalf of principal: {}", principal);

    procurementEventService.deleteSupplier(procId, eventId, supplierId, principal);

    return "OK";
  }

  @GetMapping("/{eventID}/documents")
  @TrackExecutionTime
  public Collection<DocumentSummary> getDocumentSummaries(
      @PathVariable("procID") final Integer procId, @PathVariable("eventID") final String eventId,
      final JwtAuthenticationToken authentication) {

    var principal = getPrincipalFromJwt(authentication);
    log.info("getDocumentSummaries invoked on behalf of principal: {}", principal);

    return procurementEventService.getDocumentSummaries(procId, eventId);
  }

  @PutMapping("/{eventID}/documents")
  @TrackExecutionTime
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
        description, principal);
  }

  @GetMapping(value = "/{eventID}/documents/{documentID}")
  @TrackExecutionTime
  public ResponseEntity<byte[]> getDocument(@PathVariable("procID") final Integer procId,
      @PathVariable("eventID") final String eventId,
      @PathVariable("documentID") final String documentId,
      final JwtAuthenticationToken authentication) {

    var principal = getPrincipalFromJwt(authentication);
    log.info("getDocument invoked on behalf of principal: {}", principal);

    var document = procurementEventService.getDocument(procId, eventId, documentId, principal);
    var documentKey = DocumentKey.fromString(documentId);

    return ResponseEntity.ok().contentType(document.getContentType())
        .header(HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"" + documentKey.getFileName() + "\"")
        .body(document.getData());
  }

  @DeleteMapping(value = "/{eventID}/documents/{documentID}")
  @TrackExecutionTime
  public StringValueResponse deleteDocument(@PathVariable("procID") final Integer procId,
      @PathVariable("eventID") final String eventId,
      @PathVariable("documentID") final String documentId,
      final JwtAuthenticationToken authentication) {

    var principal = getPrincipalFromJwt(authentication);
    log.info("deleteDocument invoked on behalf of principal: {}", principal);

    procurementEventService.deleteDocument(procId, eventId, documentId);

    return new StringValueResponse("OK");
  }

  @PutMapping("/{eventID}/publish")
  @TrackExecutionTime
  public StringValueResponse publishEvent(@PathVariable("procID") final Integer procId,
      @PathVariable("eventID") final String eventId,
      @RequestBody @Valid final PublishDates publishDates,
      final JwtAuthenticationToken authentication) {

    var principal = getPrincipalFromJwt(authentication);
    log.info("publishEvent invoked on behalf of principal: {}", principal);

    StopWatch generateUpdateDocWatch= new StopWatch();
    generateUpdateDocWatch.start();
       docGenService.generateAndUploadDocuments(procId, eventId);
    generateUpdateDocWatch.stop();
    log.info("publishEvent : Total time taken to generateAndUploadDocuments for procID {} : eventId :{} : Timetaken : {}  ", procId,eventId,generateUpdateDocWatch.getLastTaskTimeMillis());


    StopWatch publishStopWatch= new StopWatch();
    publishStopWatch.start();
    procurementEventService.publishEvent(procId, eventId, publishDates, principal);
    publishStopWatch.stop();
    log.info("publishEvent : Total time taken to publishEvent service for procID {} : eventId :{} , Timetaken : {}  ", procId,eventId,publishStopWatch.getLastTaskTimeMillis());
    return new StringValueResponse("OK");
  }

  @GetMapping("/{eventID}/documents/export")
  @TrackExecutionTime
  public ResponseEntity<StreamingResponseBody> exportDocuments(
      @PathVariable("procID") final Integer procId, @PathVariable("eventID") final String eventId,
      HttpServletResponse response, final JwtAuthenticationToken authentication) {

    var principal = getPrincipalFromJwt(authentication);
    log.info("Export documents invoked on behalf of principal: {}", principal);

    // list of attachments for download
    List<DocumentAttachment> exportDocuments =
        procurementEventService.exportDocuments(procId, eventId, principal);

    StreamingResponseBody streamResponseBody = out -> {
      final ZipOutputStream zipOutputStream = new ZipOutputStream(response.getOutputStream());
      ZipEntry zipEntry = null;
      for (DocumentAttachment documentAttachment : exportDocuments) {
        zipEntry = new ZipEntry(documentAttachment.getFileName());
        zipOutputStream.putNextEntry(zipEntry);
        try (InputStream is = new ByteArrayInputStream(documentAttachment.getData())) {
          IOUtils.copy(is, zipOutputStream);
        }
      }
      // set zip size in response
      response.setContentLength((int) (zipEntry != null ? zipEntry.getSize() : 0));
      if (zipOutputStream != null) {
        zipOutputStream.close();
      }
    };
    response.setContentType("application/zip");
    response.setHeader("Content-Disposition",
        "attachment; filename=" + EXPORT_BUYER_DOCUMENTS_NAME + ".zip");
    response.addHeader("Pragma", "no-cache");
    response.addHeader("Expires", "0");
    return ResponseEntity.ok(streamResponseBody);
  }

  @PutMapping("/{eventID}/publish/extend")
  @TrackExecutionTime
  public StringValueResponse extendEvent(@PathVariable("procID") final Integer procId,
      @PathVariable("eventID") final String eventId,
      @RequestBody @Valid final ExtendCriteria extendCriteria,
      final JwtAuthenticationToken authentication) {

    var principal = getPrincipalFromJwt(authentication);
    log.info("extendEvent invoked on behalf of principal: {}", principal);

    procurementEventService.extendEvent(procId, eventId, extendCriteria, principal);
    return new StringValueResponse("OK");
  }

  @GetMapping("/{eventID}/responses")
  @TrackExecutionTime
  public ResponseSummary getSupplierResponses(@PathVariable("procID") final Integer procId,
      @PathVariable("eventID") final String eventId, final JwtAuthenticationToken authentication) {

    var principal = getPrincipalFromJwt(authentication);
    log.info("getDocumentSummaries invoked on behalf of principal: {}", principal);

    return procurementEventService.getSupplierResponses(procId, eventId);
  }

  @PutMapping("/{eventID}/termination")
  @TrackExecutionTime
  public StringValueResponse terminateEvent(@PathVariable("procID") final Integer procId,
      @PathVariable("eventID") final String eventId,
      @RequestBody @Valid final TerminationEvent type,
      final JwtAuthenticationToken authentication) {

    var principal = getPrincipalFromJwt(authentication);
    log.info("terminateEvent invoked on behalf of principal: {}", principal);

    eventTransitionService.terminateEvent(procId, eventId, type.getTerminationType(), principal, true);
    return new StringValueResponse("OK");
  }

  @GetMapping("/{eventID}/responses/export")
  @TrackExecutionTime
  public  ResponseEntity<StreamingResponseBody> exportAllResponses(
          @PathVariable("procID") final Integer procId, @PathVariable("eventID") final String eventId,
          HttpServletResponse response, final JwtAuthenticationToken authentication){

    var principal = getPrincipalFromJwt(authentication);
    log.info("getDocumentSummaries invoked on behalf of principal: {}", principal);

    List<SupplierAttachmentResponse> supplierAttachmentResponseList=procurementEventService.getSupplierAttachmentResponses(principal,procId, eventId);

    StreamingResponseBody streamResponseBody =
        out -> {
          final ZipOutputStream zipOutputStream = new ZipOutputStream(response.getOutputStream());
          ZipEntry zipEntry = null;

          for (SupplierAttachmentResponse supplierAttachmentResponse :
              supplierAttachmentResponseList) {

            zipEntry = getZipEntryForSupplierResponse(supplierAttachmentResponse, zipOutputStream, zipEntry);
          }
          // set zip size in response
          response.setContentLength((int) (zipEntry != null ? zipEntry.getSize() : 0));

          if (zipOutputStream != null) {
            zipOutputStream.close();
          }
        };

    response.setContentType("application/zip");
    response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=" + String.format(EXPORT_SUPPLIER_RESPONSE_DOCUMENTS_NAME,eventId) + ".zip");
    response.addHeader(HttpHeaders.PRAGMA, "no-cache");
    response.addHeader(HttpHeaders.EXPIRES, "0");
    return ResponseEntity.ok(streamResponseBody);

  }

  @GetMapping("/{eventID}/responses/{supplierID}/export")
  @TrackExecutionTime
  public  ResponseEntity<StreamingResponseBody> exportSupplierResponse(
          @PathVariable("procID") final Integer procId, @PathVariable("eventID") final String eventId,
          @PathVariable("supplierID") final String supplierId,
          HttpServletResponse response, final JwtAuthenticationToken authentication){

    var principal = getPrincipalFromJwt(authentication);
    log.info("getResponses invoked on behalf of principal: {}", principal);

    SupplierAttachmentResponse supplierAttachmentResponse = procurementEventService.getSupplierAttachmentResponse(principal, procId, eventId, supplierId);

    StreamingResponseBody streamResponseBody = out -> {
      final ZipOutputStream zipOutputStream = new ZipOutputStream(response.getOutputStream());
      ZipEntry zipEntry = null;

      zipEntry = getZipEntryForSupplierResponse(supplierAttachmentResponse, zipOutputStream, zipEntry);
      // set zip size in response
      response.setContentLength((int) (zipEntry != null ? zipEntry.getSize() : 0));
      if (zipOutputStream != null) {
        zipOutputStream.close();
      }
    };
    response.setContentType("application/zip");
    response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=" + String.format(EXPORT_SINGLE_SUPPLIER_RESPONSE_DOCUMENTS_NAME,eventId,supplierAttachmentResponse.getSupplierName().replaceAll("\\s","_")) + ".zip");
    response.addHeader(HttpHeaders.PRAGMA, "no-cache");
    response.addHeader(HttpHeaders.EXPIRES, "0");
    return ResponseEntity.ok(streamResponseBody);

  }

  private ZipEntry getZipEntryForSupplierResponse(SupplierAttachmentResponse supplierAttachmentResponse, ZipOutputStream zipOutputStream, ZipEntry zipEntry) throws IOException {
    List<ParameterInfo> parameterInfoList =
            supplierAttachmentResponse.getParameterInfoList();

    for (ParameterInfo parameterInfo : parameterInfoList) {
      for (AttachmentInfo attachmentInfo : parameterInfo.getAttachmentInfoList()) {
        var docAttachment  =
                procurementEventService.downloadAttachment(
                        Integer.parseInt(attachmentInfo.getAttachmentId()),
                        attachmentInfo.getAttachmentName());

        var filename =
            String.join(
                "_",
                supplierAttachmentResponse.getSupplierId().toString(),
                attachmentInfo.getParameterId().toString(),
                supplierAttachmentResponse.getSupplierName());
        filename =
            filename.concat(
                attachmentInfo
                    .getAttachmentName()
                    .substring(attachmentInfo.getAttachmentName().indexOf('.')));

        zipEntry =
                new ZipEntry(filename);

        zipOutputStream.putNextEntry(zipEntry);
        try (InputStream is = new ByteArrayInputStream(docAttachment.getData())) {
          IOUtils.copy(is, zipOutputStream);
        }
      }
    }
    return zipEntry;
  }
}
