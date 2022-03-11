package uk.gov.crowncommercial.dts.scale.cat.service;

import java.util.Collection;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.commons.io.IOUtils;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import uk.gov.crowncommercial.dts.scale.cat.config.Constants;
import uk.gov.crowncommercial.dts.scale.cat.exception.ResourceNotFoundException;
import uk.gov.crowncommercial.dts.scale.cat.model.DocumentAttachment;
import uk.gov.crowncommercial.dts.scale.cat.model.DocumentKey;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.DocumentAudienceType;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.DocumentSummary;

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
  static final String FILENAME_FMT_DRAFT_PROFORMA = "%d-%s-%s.odt";

  private final ValidationService validationService;
  private final DocumentTemplateSourceService documentTemplateSourceService;
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
    var resources = documentTemplateSourceService.getEventTypeTemplates(event.getEventType());

    return resources.stream().map(r -> {
      var docKey = new DocumentKey(Math.abs(r.getFilename().hashCode()), r.getFilename(),
          DocumentAudienceType.BUYER);

      return new DocumentSummary().fileName(r.getFilename()).id(docKey.getDocumentId())
          .fileSize(getResourceLength(r))
          .description(String.format(FMT_TEMPLATE_DESCRIPTION, event.getEventType()))
          .audience(docKey.getAudience());
    }).collect(Collectors.toSet());
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
    var proformaTemplate = findProformaTemplate(event, documentKey);

    return DocumentAttachment.builder().data(IOUtils.toByteArray(proformaTemplate.getInputStream()))
        .contentType(Constants.MEDIA_TYPE_ODT).build();
  }

  /**
   * Gets a specific template document and delegates to the document generation service to generate
   * a draft proforma, and returns it
   *
   * @param procId
   * @param eventId
   * @param documentKey
   * @return a document attachment containing the draft proforma document
   */
  public DocumentAttachment getDraftProforma(final Integer procId, final String eventId,
      final DocumentKey documentKey) {

    var event = validationService.validateProjectAndEventIds(procId, eventId);
    var proformaTemplate = findProformaTemplate(event, documentKey);
    var draftProformaDocument = docGenService.generateProforma(event, proformaTemplate);
    var fileName = String.format(FILENAME_FMT_DRAFT_PROFORMA, event.getProject().getId(),
        event.getEventType(), event.getProject().getProjectName());

    return DocumentAttachment.builder().data(draftProformaDocument.toByteArray())
        .contentType(Constants.MEDIA_TYPE_ODT).fileName(fileName).build();
  }

  private Resource findProformaTemplate(final ProcurementEvent event,
      final DocumentKey documentKey) {
    var resources = documentTemplateSourceService.getEventTypeTemplates(event.getEventType());
    validateEventTypeResources(event.getEventType(), resources);

    return resources.stream()
        .filter(r -> Objects.equals(documentKey.getFileName(), r.getFilename())).findFirst()
        .orElseThrow(
            () -> new ResourceNotFoundException(String.format(ERR_MSG_FMT_TEMPLATE_NOT_FOUND,
                documentKey.getDocumentId(), documentKey.getFileName(), event.getEventType())));
  }

  private void validateEventTypeResources(final String eventType,
      final Collection<Resource> resources) {
    if (resources == null || resources.isEmpty()) {
      throw new ResourceNotFoundException(
          String.format(ERR_MSG_FMT_NO_TEMPLATES_FOR_EVENT_TYPE, eventType));
    }
  }

  @SneakyThrows
  private long getResourceLength(final Resource resource) {
    return resource.contentLength();
  }

}
