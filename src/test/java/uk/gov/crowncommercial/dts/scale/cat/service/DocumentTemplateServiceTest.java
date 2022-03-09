package uk.gov.crowncommercial.dts.scale.cat.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.Resource;
import uk.gov.crowncommercial.dts.scale.cat.config.Constants;
import uk.gov.crowncommercial.dts.scale.cat.exception.ResourceNotFoundException;
import uk.gov.crowncommercial.dts.scale.cat.model.DocumentKey;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementProject;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.DocumentAudienceType;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.DocumentSummary;

/**
 *
 */
@SpringBootTest(classes = {DocumentTemplateService.class}, webEnvironment = WebEnvironment.NONE)
class DocumentTemplateServiceTest {

  private static final Integer PROC_PROJECT_ID = 1;
  private static final String EVENT_ID = "ocds-b5fd17-1";
  private static final String PROJECT_NAME = "Test RFI project";
  private static final String TEMPLATE_RESOURCE1_FILENAME = "RFI_template1.odt";
  private static final String TEMPLATE_RESOURCE2_FILENAME = "RFI_template2.odt";
  private static final String TEMPLATE_RESOURCE1_ID =
      "YnV5ZXItNDA2MzI4NDE4LVJGSV90ZW1wbGF0ZTEub2R0";
  private static final String ERR_MSG_TEMPLATE_NOT_FOUND_FOR_EVENT_TYPE =
      "No templates found for event type [RFI]";
  private static final String ERR_MSG_TEMPLATE_NOT_FOUND = "Template [ID: " + TEMPLATE_RESOURCE1_ID
      + ", Filename: " + TEMPLATE_RESOURCE1_FILENAME + "] not found for event type [RFI]";
  private static final byte[] TEMPLATE_RESOURCE1_CONTENT = new byte[] {1, 2, 3, 'a', 'b', 'c'};
  private static final byte[] TEMPLATE_RESOURCE2_CONTENT = new byte[] {'a', 'b', 'c', 1, 2, 3};
  private static final byte[] DRAFT_PROFORMA_CONTENT = new byte[] {'d', 'e', 'f', 4, 5, 6};

  @Autowired
  private DocumentTemplateService documentTemplateService;

  @MockBean
  private ValidationService validationService;

  @MockBean
  private DocumentTemplateSourceService documentTemplateSourceService;

  @MockBean
  private DocGenService docGenService;

  @MockBean(name = "templateResource1")
  private Resource templateResource1;

  @MockBean(name = "templateResource2")
  private Resource templateResource2;

  @BeforeEach
  void beforeEach() throws IOException {
    // Mock resource behaviour
    when(templateResource1.getFilename()).thenReturn(TEMPLATE_RESOURCE1_FILENAME);
    when(templateResource2.getFilename()).thenReturn(TEMPLATE_RESOURCE2_FILENAME);

    when(templateResource1.contentLength()).thenReturn(1024L);
    when(templateResource2.contentLength()).thenReturn(512L);

    when(templateResource1.getInputStream())
        .thenReturn(new ByteArrayInputStream(TEMPLATE_RESOURCE1_CONTENT));
    when(templateResource2.getInputStream())
        .thenReturn(new ByteArrayInputStream(TEMPLATE_RESOURCE2_CONTENT));
  }

  @Test
  void testGetTemplates() throws Exception {

    var procurementEvent = ProcurementEvent.builder().eventType("RFI").build();

    when(validationService.validateProjectAndEventIds(PROC_PROJECT_ID, EVENT_ID))
        .thenReturn(procurementEvent);
    when(documentTemplateSourceService.getEventTypeTemplates(procurementEvent.getEventType()))
        .thenReturn(Set.of(templateResource1, templateResource2));

    var documentSummaries = documentTemplateService.getTemplates(PROC_PROJECT_ID, EVENT_ID);

    var docSummary1 = new DocumentSummary().fileName(TEMPLATE_RESOURCE1_FILENAME)
        .id(TEMPLATE_RESOURCE1_ID).fileSize(1024L).description("Proforma Bid Pack Document (RFI)")
        .audience(DocumentAudienceType.BUYER);

    var docSummary2 = new DocumentSummary().fileName(TEMPLATE_RESOURCE2_FILENAME)
        .id("YnV5ZXItNDA1NDA0ODk3LVJGSV90ZW1wbGF0ZTIub2R0").fileSize(512L)
        .description("Proforma Bid Pack Document (RFI)").audience(DocumentAudienceType.BUYER);
    assertEquals(2, documentSummaries.size());
    assertEquals(Set.of(docSummary1, docSummary2), documentSummaries);
  }

  @Test
  void testGetTemplate() throws Exception {
    var documentKey = DocumentKey.fromString(TEMPLATE_RESOURCE1_ID);
    var procurementEvent = ProcurementEvent.builder().eventType("RFI").build();

    when(validationService.validateProjectAndEventIds(PROC_PROJECT_ID, EVENT_ID))
        .thenReturn(procurementEvent);
    when(documentTemplateSourceService.getEventTypeTemplates(procurementEvent.getEventType()))
        .thenReturn(Set.of(templateResource1, templateResource2));

    var template = documentTemplateService.getTemplate(PROC_PROJECT_ID, EVENT_ID, documentKey);

    assertArrayEquals(TEMPLATE_RESOURCE1_CONTENT, template.getData());
    assertEquals(Constants.MEDIA_TYPE_ODT, template.getContentType());
  }

  @Test
  void testGetTemplateNotFoundForEventType() throws Exception {
    var documentKey = DocumentKey.fromString(TEMPLATE_RESOURCE1_ID);

    when(validationService.validateProjectAndEventIds(PROC_PROJECT_ID, EVENT_ID))
        .thenThrow(new ResourceNotFoundException(ERR_MSG_TEMPLATE_NOT_FOUND_FOR_EVENT_TYPE));

    var ex = assertThrows(ResourceNotFoundException.class,
        () -> documentTemplateService.getTemplate(PROC_PROJECT_ID, EVENT_ID, documentKey));

    assertEquals(ERR_MSG_TEMPLATE_NOT_FOUND_FOR_EVENT_TYPE, ex.getMessage());
  }

  @Test
  void testGetTemplateNotFound() throws Exception {
    var documentKey = DocumentKey.fromString(TEMPLATE_RESOURCE1_ID);
    var procurementEvent = ProcurementEvent.builder().eventType("RFI").build();

    when(validationService.validateProjectAndEventIds(PROC_PROJECT_ID, EVENT_ID))
        .thenReturn(procurementEvent);
    when(documentTemplateSourceService.getEventTypeTemplates(procurementEvent.getEventType()))
        .thenReturn(Set.of(templateResource2));

    var ex = assertThrows(ResourceNotFoundException.class,
        () -> documentTemplateService.getTemplate(PROC_PROJECT_ID, EVENT_ID, documentKey));

    assertEquals(ERR_MSG_TEMPLATE_NOT_FOUND, ex.getMessage());
  }

  @Test
  void testGetDraftProforma() throws Exception {
    var documentKey = DocumentKey.fromString(TEMPLATE_RESOURCE1_ID);
    var procurementProject =
        ProcurementProject.builder().id(PROC_PROJECT_ID).projectName(PROJECT_NAME).build();
    var procurementEvent =
        ProcurementEvent.builder().eventType("RFI").project(procurementProject).build();
    var draftProformaOutputStream = new ByteArrayOutputStream();
    draftProformaOutputStream.write(DRAFT_PROFORMA_CONTENT);

    when(validationService.validateProjectAndEventIds(PROC_PROJECT_ID, EVENT_ID))
        .thenReturn(procurementEvent);
    when(documentTemplateSourceService.getEventTypeTemplates(procurementEvent.getEventType()))
        .thenReturn(Set.of(templateResource1, templateResource2));
    when(docGenService.generateProforma(procurementEvent, templateResource1))
        .thenReturn(draftProformaOutputStream);

    var draftProforma =
        documentTemplateService.getDraftProforma(PROC_PROJECT_ID, EVENT_ID, documentKey);

    assertArrayEquals(DRAFT_PROFORMA_CONTENT, draftProforma.getData());
    assertEquals(Constants.MEDIA_TYPE_ODT, draftProforma.getContentType());
    assertEquals(PROC_PROJECT_ID + "-RFI-Test RFI project.odt", draftProforma.getFileName());
  }

}
