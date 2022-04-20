package uk.gov.crowncommercial.dts.scale.cat.service;

import java.util.Collection;
import java.util.HashSet;
import org.apache.commons.io.IOUtils;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import uk.gov.crowncommercial.dts.scale.cat.config.Constants;
import uk.gov.crowncommercial.dts.scale.cat.exception.ResourceNotFoundException;
import uk.gov.crowncommercial.dts.scale.cat.model.DocumentAttachment;
import uk.gov.crowncommercial.dts.scale.cat.model.DocumentKey;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.DocumentTemplate;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.DocumentAudienceType;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.DocumentSummary;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;

/**
 *
 */
@Service
@RequiredArgsConstructor
public class DocumentTemplateService {

  static final String FMT_TEMPLATE_DESCRIPTION = "Proforma Bid Pack Document (%s)";
  static final String ERR_MSG_FMT_NO_TEMPLATES_FOR_EVENT_TYPE =
      "No templates found for event type [%s]";
  static final String ERR_MSG_FMT_TEMPLATE_NOT_FOUND =
      "Template [ID: %s, Filename: %s] not found for event type [%s]";

  // {project ID}-{event type}-{project name}.odt
  static final String FILENAME_FMT_DRAFT = "%d-%s-%s.odt";

  private final RetryableTendersDBDelegate retryableTendersDBDelegate;
  private final ValidationService validationService;
  private final DocumentTemplateResourceService documentTemplateResourceService;
  private final DocGenService docGenService;

  /**
   * Lists the template documents for a given procurement event (by event type).
   *
   * @param procId
   * @param eventId
   * @return a collection of document summaries
   */
  public Collection<DocumentSummary> getTemplates(final Integer procId, final String eventId) {

    var event = validationService.validateProjectAndEventIds(procId, eventId);

    var documentSummaries = new HashSet<DocumentSummary>();
    for (DocumentTemplate documentTemplate : retryableTendersDBDelegate
        .findByEventType(event.getEventType())) {

      var templateResource =
          documentTemplateResourceService.getResource(documentTemplate.getTemplateUrl());

      var docKey = new DocumentKey(documentTemplate.getId(), templateResource.getFilename(),
          DocumentAudienceType.BUYER);
      documentSummaries.add(new DocumentSummary().fileName(templateResource.getFilename())
          .id(docKey.getDocumentId()).fileSize(getResourceLength(templateResource))
          .description(String.format(FMT_TEMPLATE_DESCRIPTION, event.getEventType()))
          .audience(docKey.getAudience()));
    }

    return documentSummaries;
  }

  /**
   * Gets a specific template document
   *
   * @param procId
   * @param eventId
   * @param documentKey containing the template ID anf filename
   * @return a document attachment containing the template file
   */
  @SneakyThrows
  public DocumentAttachment getTemplate(final Integer procId, final String eventId,
      final DocumentKey documentKey) {

    var event = validationService.validateProjectAndEventIds(procId, eventId);
    var documentTemplate = findDocumentTemplate(event, documentKey);
    var templateResource =
        documentTemplateResourceService.getResource(documentTemplate.getTemplateUrl());

    return DocumentAttachment.builder().data(IOUtils.toByteArray(templateResource.getInputStream()))
        .contentType(Constants.MEDIA_TYPE_ODT).build();
  }

  /**
   * Gets a specific document template resource and delegates to the document generation service to
   * generate a draft document, and returns it
   *
   * @param procId
   * @param eventId
   * @param documentKey
   * @return a document attachment containing the generated draft document
   */
  public DocumentAttachment getDraftDocument(final Integer procId, final String eventId,
      final DocumentKey documentKey) {

    var event = validationService.validateProjectAndEventIds(procId, eventId);
    var documentTemplate = findDocumentTemplate(event, documentKey);
    var draftDocument = docGenService.generateDocument(event, documentTemplate);
    var fileName = String.format(FILENAME_FMT_DRAFT, event.getProject().getId(),
        event.getEventType(), event.getProject().getProjectName());

    return DocumentAttachment.builder().data(draftDocument.toByteArray())
        .contentType(Constants.MEDIA_TYPE_ODT).fileName(fileName).build();
  }

  private DocumentTemplate findDocumentTemplate(final ProcurementEvent event,
      final DocumentKey documentKey) {

    return retryableTendersDBDelegate.findById(documentKey.getFileId()).orElseThrow(
        () -> new ResourceNotFoundException(String.format(ERR_MSG_FMT_TEMPLATE_NOT_FOUND,
            documentKey.getDocumentId(), documentKey.getFileName(), event.getEventType())));
  }

  @SneakyThrows
  private long getResourceLength(final Resource resource) {
    return resource.contentLength();
  }

}
