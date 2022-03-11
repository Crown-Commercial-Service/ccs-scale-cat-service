package uk.gov.crowncommercial.dts.scale.cat.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;
import uk.gov.crowncommercial.dts.scale.cat.config.ApplicationFlagsConfig;
import uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.exception.ResourceNotFoundException;
import uk.gov.crowncommercial.dts.scale.cat.model.DocumentAttachment;
import uk.gov.crowncommercial.dts.scale.cat.model.DocumentKey;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.DocumentAudienceType;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.DocumentSummary;
import uk.gov.crowncommercial.dts.scale.cat.service.DocumentTemplateService;
import uk.gov.crowncommercial.dts.scale.cat.utils.TendersAPIModelUtils;

/**
 * Controller tests for {@link DocumentTemplatesController}
 */
@WebMvcTest(DocumentTemplatesController.class)
@Import({GlobalErrorHandler.class, TendersAPIModelUtils.class, JaggaerAPIConfig.class,
    ApplicationFlagsConfig.class})
@ActiveProfiles("test")
class DocumentTemplatesControllerTest {

  private static final String DOCUMENT_TEMPLATES_PATH =
      "/tenders/projects/{procID}/events/{eventID}/documents/templates";
  private static final String PRINCIPAL = "jsmith@ccs.org.uk";
  private static final Integer PROC_PROJECT_ID = 1;
  private static final String EVENT_ID = "ocds-b5fd17-1";
  private static final String DOC_KEY = "YnV5ZXItNzYxMzg1MS1yZmlfdGVtcGxhdGUub2R0";
  private static final String ERR_MSG_TEMPLATE_NOT_FOUND = "Template not found";

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private DocumentTemplateService documentTemplateService;

  private JwtRequestPostProcessor validJwtReqPostProcessor;

  @BeforeEach
  void beforeEach() {
    validJwtReqPostProcessor = jwt().authorities(new SimpleGrantedAuthority("CAT_USER"))
        .jwt(jwt -> jwt.subject(PRINCIPAL));
  }

  @Test
  void testGetTemplatesOK() throws Exception {

    var docSummary1 = new DocumentSummary().fileName("template1.odt").id("template1DocKey")
        .fileSize(524L).description("Document Template 1").audience(DocumentAudienceType.BUYER);

    var docSummary2 = new DocumentSummary().fileName("template2.odt").id("template2DocKey")
        .fileSize(524L).description("Document Template 2").audience(DocumentAudienceType.SUPPLIER);

    when(documentTemplateService.getTemplates(PROC_PROJECT_ID, EVENT_ID))
        .thenReturn(Set.of(docSummary1, docSummary2));

    mockMvc
        .perform(get(DOCUMENT_TEMPLATES_PATH, PROC_PROJECT_ID, EVENT_ID)
            .with(validJwtReqPostProcessor).accept(APPLICATION_JSON))
        .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2))).andExpect(content()
            .json(objectMapper.writeValueAsString(Set.of(docSummary1, docSummary2)), false));
  }

  @Test
  void testGetTemplateOK() throws Exception {

    var fileContent = new byte[] {1, 2, 3, 'a', 'b', 'c'};
    var documentKey = DocumentKey.fromString(DOC_KEY);
    when(documentTemplateService.getTemplate(PROC_PROJECT_ID, EVENT_ID, documentKey))
        .thenReturn(DocumentAttachment.builder().data(fileContent)
            .contentType(MediaType.APPLICATION_OCTET_STREAM).build());

    mockMvc
        .perform(get(DOCUMENT_TEMPLATES_PATH + "/{templateID}", PROC_PROJECT_ID, EVENT_ID, DOC_KEY)
            .with(validJwtReqPostProcessor))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"" + documentKey.getFileName() + "\""))
        .andExpect(content().bytes(fileContent));
  }

  @Test
  void testGetTemplateNotFound() throws Exception {

    var documentKey = DocumentKey.fromString(DOC_KEY);
    when(documentTemplateService.getTemplate(PROC_PROJECT_ID, EVENT_ID, documentKey))
        .thenThrow(new ResourceNotFoundException(ERR_MSG_TEMPLATE_NOT_FOUND));

    mockMvc
        .perform(get(DOCUMENT_TEMPLATES_PATH + "/{templateID}", PROC_PROJECT_ID, EVENT_ID, DOC_KEY)
            .with(validJwtReqPostProcessor))
        .andExpect(status().isNotFound()).andExpect(content().contentType(APPLICATION_JSON))
        .andExpect(jsonPath("$.errors", hasSize(1)))
        .andExpect(jsonPath("$.errors[0].status", is("404 NOT_FOUND")))
        .andExpect(jsonPath("$.errors[0].title", is("Resource not found")))
        .andExpect(jsonPath("$.errors[0].detail", is(ERR_MSG_TEMPLATE_NOT_FOUND)));
  }

  @Test
  void testGetDraftProformaOK() throws Exception {

    var fileContent = new byte[] {1, 2, 3, 'a', 'b', 'c'};
    var fileName = "1234-RFI-TestProject.odt";
    var documentKey = DocumentKey.fromString(DOC_KEY);
    when(documentTemplateService.getDraftProforma(PROC_PROJECT_ID, EVENT_ID, documentKey))
        .thenReturn(DocumentAttachment.builder().data(fileContent)
            .contentType(MediaType.APPLICATION_OCTET_STREAM).fileName(fileName).build());

    mockMvc
        .perform(
            get(DOCUMENT_TEMPLATES_PATH + "/{templateID}/draft", PROC_PROJECT_ID, EVENT_ID, DOC_KEY)
                .with(validJwtReqPostProcessor))
        .andExpect(status().isOk()).andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"" + fileName + "\""))
        .andExpect(content().bytes(fileContent));
  }

}
