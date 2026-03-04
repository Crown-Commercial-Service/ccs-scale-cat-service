package uk.gov.crowncommercial.dts.scale.cat.controller;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.javacrumbs.shedlock.core.LockProvider;
import uk.gov.crowncommercial.dts.scale.cat.config.ApplicationFlagsConfig;
import uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.config.OAuth2Config;
import uk.gov.crowncommercial.dts.scale.cat.exception.JaggaerApplicationException;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.*;
import uk.gov.crowncommercial.dts.scale.cat.service.DocGenService;
import uk.gov.crowncommercial.dts.scale.cat.service.EventTransitionService;
import uk.gov.crowncommercial.dts.scale.cat.service.ProcurementEventService;
import uk.gov.crowncommercial.dts.scale.cat.service.QuestionAndAnswerService;
import uk.gov.crowncommercial.dts.scale.cat.service.ca.AssessmentScoreExportService;
import uk.gov.crowncommercial.dts.scale.cat.utils.TendersAPIModelUtils;

/**
 * Controller tests
 */
@WebMvcTest(EventsController.class)
@Import({TendersAPIModelUtils.class, JaggaerAPIConfig.class, ApplicationFlagsConfig.class, OAuth2Config.class})
@ActiveProfiles("test")
class EventsControllerTest {

  private static final String EVENTS_PATH = "/tenders/projects/{procID}/events";
  private static final String PRINCIPAL = "jsmith@ccs.org.uk";
  private static final Integer PROC_PROJECT_ID = 1;
  private static final String EVENT_ID = "ocds-pfhb7i-1";
  private static final String EVENT_NAME = "NAME";
  private static final String GROUP_TYPE = "MyGroupType";
  private static final String JAGGAER_ID = "1";
  private static final ViewEventType EVENT_TYPE = ViewEventType.TBD;
  private static final TenderStatus EVENT_STATUS = TenderStatus.PLANNING;
  private static final ReleaseTag EVENT_STAGE = ReleaseTag.TENDER;
  private static final String SUPPLIER_ID = "US-DUNS-227015716";

  private static final String FILE_ID = "YnV5ZXItNzYxMzg1MS1jYXQtamFnZ2Flci1maWxlLXRlc3QtMy50eHQ=";
  private static final String FILE_NAME = "test_file.pdf";
  private static final String FILE_DESCRIPTION = "Test file upload";
  private static final Long FILE_SIZE = 12345L;
  private static final DocumentAudienceType AUDIENCE = DocumentAudienceType.BUYER;

  private final CreateEvent createEvent = new CreateEvent();

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private TendersAPIModelUtils tendersAPIModelUtils;

  @Autowired
  private ObjectMapper objectMapper;

  @MockitoBean
  private ProcurementEventService procurementEventService;

  @MockitoBean
  private EventTransitionService eventTransitionService;

  @MockitoBean
  private DocGenService docGenService;

  @MockitoBean
  private AssessmentScoreExportService exportService;

  @MockitoBean
  private QuestionAndAnswerService questionAndAnswerService;

  @MockitoBean
  private Principal principal;

  @MockitoBean
  private EventSummary eventSummary;
  
  @MockitoBean
  private LockProvider lockProvider;

  private JwtRequestPostProcessor validJwtReqPostProcessor;

  @BeforeEach
  void beforeEach() {
    validJwtReqPostProcessor = jwt().authorities(new SimpleGrantedAuthority("CAT_USER"))
        .jwt(jwt -> jwt.subject(PRINCIPAL));
  }

  @Test
  void createProcurementEvent_200_OK() throws Exception {

    var eventStatus = tendersAPIModelUtils.buildEventSummary(EVENT_ID, EVENT_NAME,
        Optional.of(JAGGAER_ID), EVENT_TYPE, EVENT_STATUS, EVENT_STAGE, Optional.empty());

    when(procurementEventService.createEvent(eq(PROC_PROJECT_ID), any(CreateEvent.class),
        nullable(Boolean.class), anyString())).thenReturn(eventStatus);

    mockMvc
        .perform(post(EVENTS_PATH, PROC_PROJECT_ID).with(validJwtReqPostProcessor)
            .accept(APPLICATION_JSON).contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(createEvent)))
        .andDo(print()).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(EVENT_ID))
        .andExpect(jsonPath("$.title").value(EVENT_NAME))
        .andExpect(jsonPath("$.eventSupportId").value(JAGGAER_ID))
        .andExpect(jsonPath("$.eventType").value(EVENT_TYPE.getValue()))
        .andExpect(jsonPath("$.eventStage").value(EVENT_STAGE.getValue()))
        .andExpect(jsonPath("$.status").value(EVENT_STATUS.getValue()));
  }

  @Test
  void createProcurementEvent_401_Unauthorised() throws Exception {
    mockMvc
        .perform(post(EVENTS_PATH, PROC_PROJECT_ID).accept(APPLICATION_JSON)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(createEvent)))
        .andDo(print()).andExpect(status().isUnauthorized())
        .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")))
        .andExpect(content().contentType(APPLICATION_JSON))
        .andExpect(jsonPath("$.errors", hasSize(1)))
        .andExpect(jsonPath("$.errors[0].status", is("401 UNAUTHORIZED")))
        .andExpect(jsonPath("$.errors[0].title", is("Missing, expired or invalid access token")));
  }

  @Test
  void createProcurementEvent_403_Forbidden() throws Exception {
    var invalidJwtReqPostProcessor =
        jwt().authorities(new SimpleGrantedAuthority("OTHER")).jwt(jwt -> jwt.subject(PRINCIPAL));

    mockMvc
        .perform(post(EVENTS_PATH, PROC_PROJECT_ID).with(invalidJwtReqPostProcessor)
            .accept(APPLICATION_JSON).contentType(APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(createEvent)))
        .andDo(print()).andExpect(status().isForbidden())
        .andExpect(content().contentType(APPLICATION_JSON))
        .andExpect(jsonPath("$.errors", hasSize(1)))
        .andExpect(jsonPath("$.errors[0].status", is("403 FORBIDDEN"))).andExpect(
            jsonPath("$.errors[0].title", is("Access to the requested resource is forbidden")));
  }

  @Test
  void createProcurementEvent_500_ISE() throws Exception {

    when(procurementEventService.createEvent(PROC_PROJECT_ID, createEvent, null, PRINCIPAL))
        .thenThrow(new JaggaerApplicationException("1", "BANG"));

    mockMvc
        .perform(post(EVENTS_PATH, PROC_PROJECT_ID).with(validJwtReqPostProcessor)
            .accept(APPLICATION_JSON).contentType(APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(createEvent)))
        .andDo(print()).andExpect(status().isInternalServerError())
        .andExpect(content().contentType(APPLICATION_JSON))
        .andExpect(jsonPath("$.errors", hasSize(1)))
        .andExpect(jsonPath("$.errors[0].status", is("500 INTERNAL_SERVER_ERROR"))).andExpect(
            jsonPath("$.errors[0].title", is("An error occurred invoking an upstream service")));
  }

  @Test
  void updateProcurementEvent_200_OK() throws Exception {

    var updateEvent = new UpdateEvent();
    updateEvent.setName("New name");

    var eventSummary = tendersAPIModelUtils.buildEventSummary(EVENT_ID, EVENT_NAME,
        Optional.of(JAGGAER_ID), EVENT_TYPE, EVENT_STATUS, EVENT_STAGE, Optional.empty());

    when(procurementEventService.updateProcurementEvent(PROC_PROJECT_ID, EVENT_ID, updateEvent,
        PRINCIPAL)).thenReturn(eventSummary);

    mockMvc
        .perform(put(EVENTS_PATH + "/{eventID}", PROC_PROJECT_ID, EVENT_ID)
            .with(validJwtReqPostProcessor).contentType(APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(updateEvent)))
        .andDo(print()).andExpect(status().isOk()).andExpect(jsonPath("$.title").value(EVENT_NAME))
        .andExpect(jsonPath("$.eventSupportId").value(JAGGAER_ID))
        .andExpect(jsonPath("$.eventType").value(EVENT_TYPE.getValue()))
        .andExpect(jsonPath("$.eventStage").value(EVENT_STAGE.getValue()))
        .andExpect(jsonPath("$.status").value(EVENT_STATUS.getValue()));
  }

  @Test
  void getEvent_200_OK() throws Exception {

    var eventDetail = new EventDetail();
    when(procurementEventService.getEvent(PROC_PROJECT_ID, EVENT_ID)).thenReturn(eventDetail);

    mockMvc
        .perform(get(EVENTS_PATH + "/{eventID}", PROC_PROJECT_ID, EVENT_ID)
            .with(validJwtReqPostProcessor).accept(APPLICATION_JSON))
        .andDo(print()).andExpect(status().isOk())
        .andExpect(content().contentType(APPLICATION_JSON));
    // TODO: Verify content

    verify(procurementEventService, times(1)).getEvent(PROC_PROJECT_ID, EVENT_ID);
  }

  @Test
  void getSuppliers_200_OK() throws Exception {

    var response = new EventSuppliers();
    var org = new OrganizationReference1();
    org.setId(SUPPLIER_ID);
    List<OrganizationReference1> orgs = Arrays.asList(org);
    response.suppliers(orgs);
    when(procurementEventService.getSuppliers(PROC_PROJECT_ID, EVENT_ID)).thenReturn(response);

    mockMvc
        .perform(get(EVENTS_PATH + "/{eventID}/suppliers", PROC_PROJECT_ID, EVENT_ID)
            .with(validJwtReqPostProcessor).accept(APPLICATION_JSON))
        .andDo(print()).andExpect(status().isOk())
        .andExpect(content().contentType(APPLICATION_JSON))
        .andExpect(jsonPath("$.suppliers.[0].id").value(SUPPLIER_ID));

    verify(procurementEventService, times(1)).getSuppliers(PROC_PROJECT_ID, EVENT_ID);
  }

  @Test
  void addSupplier_200_OK() throws Exception {

    var eventSuppliers = new EventSuppliers();
    var org = new OrganizationReference1();
    org.setId(SUPPLIER_ID);
    List<OrganizationReference1> orgs = Arrays.asList(org);
    eventSuppliers.suppliers(orgs);

    when(procurementEventService.addSuppliers(PROC_PROJECT_ID, EVENT_ID, eventSuppliers, false,
        PRINCIPAL)).thenReturn(eventSuppliers);

    mockMvc
        .perform(post(EVENTS_PATH + "/{eventID}/suppliers", PROC_PROJECT_ID, EVENT_ID)
            .with(validJwtReqPostProcessor).contentType(APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(eventSuppliers)))
        .andDo(print()).andExpect(status().isOk())
        .andExpect(jsonPath("$.suppliers.[0].id").value(SUPPLIER_ID));
  }

  @Test
  void deleteSupplier_200_OK() throws Exception {

    mockMvc
        .perform(delete(EVENTS_PATH + "/{eventID}/suppliers/{suplierID}", PROC_PROJECT_ID, EVENT_ID,
            SUPPLIER_ID).with(validJwtReqPostProcessor).contentType(APPLICATION_JSON))
        .andDo(print()).andExpect(status().isOk()).andExpect(content().string("OK"));

    verify(procurementEventService, times(1)).deleteSupplier(PROC_PROJECT_ID, EVENT_ID, SUPPLIER_ID,
        PRINCIPAL);

  }

  @Test
  void getDocuments_200_OK() throws Exception {

    var doc = new DocumentSummary();
    doc.setAudience(AUDIENCE);
    doc.setId(FILE_ID);
    doc.setFileName(FILE_NAME);
    doc.setDescription(FILE_DESCRIPTION);
    doc.setFileSize(FILE_SIZE);
    List<DocumentSummary> documentSummaries = Arrays.asList(doc);

    when(procurementEventService.getDocumentSummaries(PROC_PROJECT_ID, EVENT_ID))
        .thenReturn(documentSummaries);

    mockMvc
        .perform(get(EVENTS_PATH + "/{eventID}/documents", PROC_PROJECT_ID, EVENT_ID)
            .with(validJwtReqPostProcessor).accept(APPLICATION_JSON))
        .andDo(print()).andExpect(status().isOk())
        .andExpect(content().contentType(APPLICATION_JSON))
        .andExpect(jsonPath("$[0].audience").value(AUDIENCE.getValue()))
        .andExpect(jsonPath("$[0].id").value(FILE_ID))
        .andExpect(jsonPath("$[0].fileName").value(FILE_NAME))
        .andExpect(jsonPath("$[0].fileSize").value(FILE_SIZE))
        .andExpect(jsonPath("$[0].description").value(FILE_DESCRIPTION));

    verify(procurementEventService, times(1)).getDocumentSummaries(PROC_PROJECT_ID, EVENT_ID);
  }

  @Test
  void publishEvent_200_OK() throws Exception {

    var publishDates = new PublishDates().endDate(LocalDateTime.now().atOffset(ZoneOffset.UTC));

    mockMvc
        .perform(put(EVENTS_PATH + "/{eventID}/publish", PROC_PROJECT_ID, EVENT_ID)
            .with(validJwtReqPostProcessor).contentType(APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(publishDates)))
        .andDo(print()).andExpect(status().isOk()).andExpect(jsonPath("$").value("OK"));

    verify(docGenService).generateAndUploadDocuments(PROC_PROJECT_ID, EVENT_ID);
    verify(procurementEventService).publishEvent(PROC_PROJECT_ID, EVENT_ID, publishDates,
        PRINCIPAL);
  }

  @Test
  void publishEvent_400_BadRequest_EndDate() throws Exception {
    List<String> inputValues = new ArrayList<>(Arrays.asList("2021-12", "2021-12-25T-12:00:00Z", " ", "!", ""));

    inputValues.forEach(input -> {
      var publishDates = Collections.singletonMap("endDate", input);

        try {
            mockMvc
                .perform(put(EVENTS_PATH + "/{eventID}/publish", PROC_PROJECT_ID, EVENT_ID)
                    .with(validJwtReqPostProcessor).contentType(APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(publishDates)))
                .andDo(print()).andExpect(status().isBadRequest())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].status", is("400 BAD_REQUEST")))
                .andExpect(jsonPath("$.errors[0].title", is("Validation error processing the request")));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    });
  }

  @Test
  void publishEvent_400_NullRequest_EndDate() throws Exception {
    var publishDates = Collections.singletonMap("endDate", null);

    mockMvc
            .perform(put(EVENTS_PATH + "/{eventID}/publish", PROC_PROJECT_ID, EVENT_ID)
                    .with(validJwtReqPostProcessor).contentType(APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(publishDates)))
            .andDo(print()).andExpect(status().isBadRequest())
            .andExpect(content().contentType(APPLICATION_JSON))
            .andExpect(jsonPath("$.errors", hasSize(1)))
            .andExpect(jsonPath("$.errors[0].status", is("400 BAD_REQUEST")))
            .andExpect(jsonPath("$.errors[0].title", is("Validation error processing the request")));
  }

  @Test
  void deleteEvent_200_OK() throws Exception {
    mockMvc.perform(delete(EVENTS_PATH + "/{eventID}", PROC_PROJECT_ID, EVENT_ID)
                    .with(validJwtReqPostProcessor)
                    .contentType(APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").value("OK"));

    verify(procurementEventService, times(1))
            .deleteEvent(PROC_PROJECT_ID, EVENT_ID, PRINCIPAL);
  }

  @Test
  void getEvents_200_OK() throws Exception {
    mockMvc
        .perform(get(EVENTS_PATH, PROC_PROJECT_ID).with(validJwtReqPostProcessor)
            .accept(APPLICATION_JSON))
        .andDo(print()).andExpect(status().isOk())
        .andExpect(content().contentType(APPLICATION_JSON));

    verify(procurementEventService, times(1)).getEventsForProject(PROC_PROJECT_ID, PRINCIPAL);
  }

  @Test
  void getSupplierResponses_200_OK() throws Exception {
    mockMvc.perform(get(EVENTS_PATH + "/{eventID}/responses", PROC_PROJECT_ID, EVENT_ID).with(
            validJwtReqPostProcessor).contentType(APPLICATION_JSON)).andDo(print())
        .andExpect(status().isOk());
//        .andExpect(content().contentType(APPLICATION_JSON));

    verify(procurementEventService, times(1)).getSupplierResponses(PROC_PROJECT_ID, EVENT_ID);
  }

  @Test
  void shouldGetUseQuestionGroup_Answer_True() throws Exception {
    QandA useQuestionGroups = new QandA();
    useQuestionGroups.setQuestion(GROUP_TYPE + "-use-question-groups");
    useQuestionGroups.setAnswer("TRUE");
    useQuestionGroups.id(BigDecimal.valueOf(123));

    QandAWithProjectDetails expected = new QandAWithProjectDetails();
    expected.setQandA(List.of(useQuestionGroups));

    when(questionAndAnswerService.getQuestionAndAnswerByEvent(PROC_PROJECT_ID, EVENT_ID, PRINCIPAL)).thenReturn(expected);

    mockMvc
      .perform(get(EVENTS_PATH + "/{eventID}/use-question-groups/{groupType}", PROC_PROJECT_ID, EVENT_ID, GROUP_TYPE)
        .with(validJwtReqPostProcessor).accept(APPLICATION_JSON))
      .andDo(print())
      .andExpect(status().isOk())
      .andExpect(content().string(containsString("\"useQuestionGroups\":true")))
      .andExpect(content().string(containsString("\"useQuestionGroupsQaId\":123")));

    verify(questionAndAnswerService, times(1)).getQuestionAndAnswerByEvent(PROC_PROJECT_ID, EVENT_ID, PRINCIPAL);
  }

  @Test
  void shouldGetUseQuestionGroup_Answer_False() throws Exception {
    QandA useQuestionGroups = new QandA();
    useQuestionGroups.setQuestion(GROUP_TYPE + "-use-question-groups");
    useQuestionGroups.setAnswer("FALSE");
    useQuestionGroups.id(BigDecimal.valueOf(234));

    QandAWithProjectDetails expected = new QandAWithProjectDetails();
    expected.setQandA(List.of(useQuestionGroups));

    when(questionAndAnswerService.getQuestionAndAnswerByEvent(PROC_PROJECT_ID, EVENT_ID, PRINCIPAL)).thenReturn(expected);

    mockMvc
      .perform(get(EVENTS_PATH + "/{eventID}/use-question-groups/{groupType}", PROC_PROJECT_ID, EVENT_ID, GROUP_TYPE)
        .with(validJwtReqPostProcessor).accept(APPLICATION_JSON))
      .andDo(print())
      .andExpect(status().isOk())
      .andExpect(content().string(containsString("\"useQuestionGroups\":false")))
      .andExpect(content().string(containsString("\"useQuestionGroupsQaId\":234")));

    verify(questionAndAnswerService, times(1)).getQuestionAndAnswerByEvent(PROC_PROJECT_ID, EVENT_ID, PRINCIPAL);
  }

  @Test
  void shouldGetUseQuestionGroup_Answer_Null() throws Exception {
    when(questionAndAnswerService.getQuestionAndAnswerByEvent(PROC_PROJECT_ID, EVENT_ID, PRINCIPAL)).thenReturn(null);

    mockMvc
      .perform(get(EVENTS_PATH + "/{eventID}/use-question-groups/{groupType}", PROC_PROJECT_ID, EVENT_ID, GROUP_TYPE)
        .with(validJwtReqPostProcessor).accept(APPLICATION_JSON))
      .andDo(print())
      .andExpect(status().isOk())
      .andExpect(content().string(not(containsString("useQuestionGroups"))))
      .andExpect(content().string(not(containsString("useQuestionGroupsQaId"))));

    verify(questionAndAnswerService, times(1)).getQuestionAndAnswerByEvent(PROC_PROJECT_ID, EVENT_ID, PRINCIPAL);
  }

  @Test
  void shouldGetUseQuestionGroup_Answer_Null_ForNullGroupType() throws Exception {
    final String nullGroupType = null;

    when(questionAndAnswerService.getQuestionAndAnswerByEvent(PROC_PROJECT_ID, EVENT_ID, PRINCIPAL)).thenReturn(null);

    mockMvc
      .perform(get(EVENTS_PATH + "/{eventID}/use-question-groups/{groupType}", PROC_PROJECT_ID, EVENT_ID, nullGroupType)
        .with(validJwtReqPostProcessor).accept(APPLICATION_JSON))
      .andDo(print())
      .andExpect(status().is5xxServerError())
      .andExpect(content().string(not(containsString("useQuestionGroups"))))
      .andExpect(content().string(not(containsString("useQuestionGroupsQaId"))));

    verifyNoInteractions(questionAndAnswerService);
  }

  @Test
  void shouldSaveUseQuestionGroup_AnswerTrue() throws Exception {
    QandA useQuestionGroupsQandA = new QandA();
    useQuestionGroupsQandA.setQuestion(GROUP_TYPE + "-use-question-groups");
    useQuestionGroupsQandA.setAnswer("true");

    when(questionAndAnswerService.getQuestionAndAnswerByEvent(PROC_PROJECT_ID, EVENT_ID, PRINCIPAL)).thenReturn(null);
    when(questionAndAnswerService.createOrUpdateQuestionAndAnswer(PRINCIPAL, PROC_PROJECT_ID, EVENT_ID, useQuestionGroupsQandA, null)).thenReturn(null);

    QuestionGroupNamesWrite useQuestionGroups = new QuestionGroupNamesWrite();
    useQuestionGroups.setUseQuestionGroups(true);
    useQuestionGroups.setUseQuestionGroupsQaId(123);

    mockMvc
    .perform(post(EVENTS_PATH + "/{eventID}/use-question-groups/{groupType}", PROC_PROJECT_ID, EVENT_ID, GROUP_TYPE)
      .with(validJwtReqPostProcessor).accept(APPLICATION_JSON)
      .contentType(MediaType.APPLICATION_JSON)
      .content(objectMapper.writeValueAsString(useQuestionGroups)))
    .andDo(print())
    .andExpect(status().isOk())
    .andExpect(content().string(containsString("OK")));

    verify(questionAndAnswerService, times(1)).getQuestionAndAnswerByEvent(PROC_PROJECT_ID, EVENT_ID, PRINCIPAL);
    verify(questionAndAnswerService, times(1)).createOrUpdateQuestionAndAnswer(PRINCIPAL, PROC_PROJECT_ID, EVENT_ID, useQuestionGroupsQandA, null);
  }

  @Test
  void shouldFailToSaveUseQuestionGroup_NoGroupType() throws Exception {
    QuestionGroupNamesWrite useQuestionGroups = new QuestionGroupNamesWrite();
    useQuestionGroups.setUseQuestionGroups(true);
    useQuestionGroups.setUseQuestionGroupsQaId(123);

    mockMvc
    .perform(post(EVENTS_PATH + "/{eventID}/use-question-groups/{groupType}", PROC_PROJECT_ID, EVENT_ID, "")
      .with(validJwtReqPostProcessor).accept(APPLICATION_JSON)
      .contentType(MediaType.APPLICATION_JSON)
      .content(objectMapper.writeValueAsString(useQuestionGroups)))
    .andDo(print())
    .andExpect(status().is5xxServerError());

    verifyNoInteractions(questionAndAnswerService);
  }

  @Test
  void shouldFailToSaveUseQuestionGroup_NoData() throws Exception {
    when(questionAndAnswerService.getQuestionAndAnswerByEvent(PROC_PROJECT_ID, EVENT_ID, PRINCIPAL)).thenReturn(null);

    mockMvc
    .perform(post(EVENTS_PATH + "/{eventID}/use-question-groups/{groupType}", PROC_PROJECT_ID, EVENT_ID, GROUP_TYPE)
      .with(validJwtReqPostProcessor).accept(APPLICATION_JSON)
      .contentType(MediaType.APPLICATION_JSON)
      .content(objectMapper.writeValueAsString(new QuestionGroupNamesWrite())))
    .andDo(print())
    .andExpect(status().isOk())
    .andExpect(content().string(containsString("ERROR")));

    verify(questionAndAnswerService, times(1)).getQuestionAndAnswerByEvent(PROC_PROJECT_ID, EVENT_ID, PRINCIPAL);
    verifyNoMoreInteractions(questionAndAnswerService);
  }

  @Test
  void shouldGetQuestionGroups() throws Exception {
    QandA questionGroup1 = new QandA();
    questionGroup1.setQuestion(GROUP_TYPE + "-question-group-0");
    questionGroup1.setAnswer("group1");
    questionGroup1.id(BigDecimal.valueOf(123));

    QandA questionGroup2 = new QandA();
    questionGroup2.setQuestion(GROUP_TYPE + "-question-group-1");
    questionGroup2.setAnswer("group2");
    questionGroup2.id(BigDecimal.valueOf(456));

    QandA questionGroup3 = new QandA();
    questionGroup3.setQuestion(GROUP_TYPE + "-question-group-2");
    questionGroup3.setAnswer("group3");
    questionGroup3.id(BigDecimal.valueOf(789));

    QandAWithProjectDetails expected = new QandAWithProjectDetails();
    // note we explicitly return the entries out-of-order
    expected.setQandA(List.of(questionGroup3, questionGroup2, questionGroup1));

    when(questionAndAnswerService.getQuestionAndAnswerByEvent(PROC_PROJECT_ID, EVENT_ID, PRINCIPAL)).thenReturn(expected);

    mockMvc
      .perform(get(EVENTS_PATH + "/{eventID}/question-groups/{groupType}", PROC_PROJECT_ID, EVENT_ID, GROUP_TYPE)
        .with(validJwtReqPostProcessor).accept(APPLICATION_JSON))
      .andDo(print())
      .andExpect(status().isOk())
      .andExpect(content().string(containsString("\"questionGroups\":[\"group1\",\"group2\",\"group3\"]")))
      .andExpect(content().string(containsString("\"qaIds\":[\"123\",\"456\",\"789\"]")));

    verify(questionAndAnswerService, times(1)).getQuestionAndAnswerByEvent(PROC_PROJECT_ID, EVENT_ID, PRINCIPAL);
  }

  @Test
  void shouldGetQuestionGroups_NoData_OnNoMatch() throws Exception {
    QandA qanda = new QandA();
    qanda.setQuestion(GROUP_TYPE + "-something");
    qanda.setAnswer("something");
    qanda.id(BigDecimal.valueOf(123));

    QandAWithProjectDetails expected = new QandAWithProjectDetails();
    expected.setQandA(List.of(qanda));

    when(questionAndAnswerService.getQuestionAndAnswerByEvent(PROC_PROJECT_ID, EVENT_ID, PRINCIPAL)).thenReturn(expected);

    mockMvc
      .perform(get(EVENTS_PATH + "/{eventID}/question-groups/{groupType}", PROC_PROJECT_ID, EVENT_ID, GROUP_TYPE)
        .with(validJwtReqPostProcessor).accept(APPLICATION_JSON))
      .andDo(print())
      .andExpect(status().isOk())
      .andExpect(content().string(containsString("\"questionGroups\":null")))
      .andExpect(content().string(containsString("\"qaIds\":null")));

    verify(questionAndAnswerService, times(1)).getQuestionAndAnswerByEvent(PROC_PROJECT_ID, EVENT_ID, PRINCIPAL);
  }

  @Test
  void shouldGetQuestionGroups_NoData() throws Exception {
    when(questionAndAnswerService.getQuestionAndAnswerByEvent(PROC_PROJECT_ID, EVENT_ID, PRINCIPAL)).thenReturn(null);

    mockMvc
      .perform(get(EVENTS_PATH + "/{eventID}/question-groups/{groupType}", PROC_PROJECT_ID, EVENT_ID, GROUP_TYPE)
        .with(validJwtReqPostProcessor).accept(APPLICATION_JSON))
      .andDo(print())
      .andExpect(status().isOk())
      .andExpect(content().string(not(containsString("questionGroups"))))
      .andExpect(content().string(not(containsString("qaIds"))));

    verify(questionAndAnswerService, times(1)).getQuestionAndAnswerByEvent(PROC_PROJECT_ID, EVENT_ID, PRINCIPAL);
  }

  @Test
  void shouldGetQuestionGroup_Answer_Null_ForNullGroupType() throws Exception {
    final String nullGroupType = null;

    when(questionAndAnswerService.getQuestionAndAnswerByEvent(PROC_PROJECT_ID, EVENT_ID, PRINCIPAL)).thenReturn(null);

    mockMvc
      .perform(get(EVENTS_PATH + "/{eventID}/question-groups/{groupType}", PROC_PROJECT_ID, EVENT_ID, nullGroupType)
        .with(validJwtReqPostProcessor).accept(APPLICATION_JSON))
      .andDo(print())
      .andExpect(status().is5xxServerError())
      .andExpect(content().string(not(containsString("questionGroups"))))
      .andExpect(content().string(not(containsString("qaIds"))));

    verifyNoMoreInteractions(questionAndAnswerService);
  }

  @Test
  void shouldSaveQuestionGroup() throws Exception {
    QandA expectedQuestionGroup1 = new QandA();
    expectedQuestionGroup1.setQuestion(GROUP_TYPE + "-question-group-0");
    expectedQuestionGroup1.setAnswer("group1");
    expectedQuestionGroup1.id(BigDecimal.valueOf(123));

    QandA expectedQuestionGroup2 = new QandA();
    expectedQuestionGroup2.setQuestion(GROUP_TYPE + "-question-group-1");
    expectedQuestionGroup2.setAnswer("group2");
    expectedQuestionGroup2.id(BigDecimal.valueOf(456));

    QandAWithProjectDetails expected = new QandAWithProjectDetails();
    expected.setQandA(List.of(expectedQuestionGroup1, expectedQuestionGroup2));

    when(questionAndAnswerService.getQuestionAndAnswerByEvent(PROC_PROJECT_ID, EVENT_ID, PRINCIPAL)).thenReturn(expected);

    String question = "Select question group";
    doNothing().when(questionAndAnswerService).deleteSpecificAnswersForGivenQuestion(PRINCIPAL, PROC_PROJECT_ID, EVENT_ID, question, Collections.emptyList());

    QandA questionGroup1 = new QandA();
    questionGroup1.setQuestion(GROUP_TYPE + "-question-group-0");
    questionGroup1.setAnswer("group1");

    QandA questionGroup2 = new QandA();
    questionGroup2.setQuestion(GROUP_TYPE + "-question-group-1");
    questionGroup2.setAnswer("group2");

    QuestionGroupNamesWrite request = new QuestionGroupNamesWrite();
    request.setQuestionGroups(List.of("group1", "group2"));
    request.setQaIds(List.of(123, 456));

    mockMvc
    .perform(post(EVENTS_PATH + "/{eventID}/question-groups/{groupType}", PROC_PROJECT_ID, EVENT_ID, GROUP_TYPE)
      .with(validJwtReqPostProcessor).accept(APPLICATION_JSON)
      .contentType(MediaType.APPLICATION_JSON)
      .content(objectMapper.writeValueAsString(request)))
    .andDo(print())
    .andExpect(status().isOk())
    .andExpect(content().string(containsString("OK")));

    verify(questionAndAnswerService).getQuestionAndAnswerByEvent(PROC_PROJECT_ID, EVENT_ID, PRINCIPAL);

    verify(questionAndAnswerService, times(1)).deleteQuestionAndAnswerByQaIdFromRepo(PROC_PROJECT_ID, EVENT_ID, 123, PRINCIPAL);
    verify(questionAndAnswerService, times(1)).deleteQuestionAndAnswerByQaIdFromRepo(PROC_PROJECT_ID, EVENT_ID, 456, PRINCIPAL);

    verify(questionAndAnswerService, times(1)).createOrUpdateQuestionAndAnswer(PRINCIPAL, PROC_PROJECT_ID, EVENT_ID, questionGroup1, null);
    verify(questionAndAnswerService, times(1)).createOrUpdateQuestionAndAnswer(PRINCIPAL, PROC_PROJECT_ID, EVENT_ID, questionGroup2, null);

    verify(questionAndAnswerService).deleteSpecificAnswersForGivenQuestion(PRINCIPAL, PROC_PROJECT_ID, EVENT_ID, question, Collections.emptyList());

    verifyNoMoreInteractions(questionAndAnswerService);
  }

  @Test
  void shouldNotSaveQuestionGroup_NoData() throws Exception {
    when(questionAndAnswerService.getQuestionAndAnswerByEvent(PROC_PROJECT_ID, EVENT_ID, PRINCIPAL)).thenReturn(null);

    mockMvc
    .perform(post(EVENTS_PATH + "/{eventID}/question-groups/{groupType}", PROC_PROJECT_ID, EVENT_ID, GROUP_TYPE)
      .with(validJwtReqPostProcessor).accept(APPLICATION_JSON)
      .contentType(MediaType.APPLICATION_JSON)
      .content(objectMapper.writeValueAsString(new QuestionGroupNamesWrite())))
    .andDo(print())
    .andExpect(status().isOk())
    .andExpect(content().string(containsString("ERROR")));

    verify(questionAndAnswerService).getQuestionAndAnswerByEvent(PROC_PROJECT_ID, EVENT_ID, PRINCIPAL);
    verifyNoMoreInteractions(questionAndAnswerService);
  }

  @Test
  void shouldNotSaveQuestionGroup_NoQuestionGroup() throws Exception {
    final String nullGroupType = null;

    mockMvc
    .perform(post(EVENTS_PATH + "/{eventID}/question-groups/{groupType}", PROC_PROJECT_ID, EVENT_ID, nullGroupType)
      .with(validJwtReqPostProcessor).accept(APPLICATION_JSON)
      .contentType(MediaType.APPLICATION_JSON)
      .content(objectMapper.writeValueAsString(new QuestionGroupNamesWrite())))
    .andDo(print())
    .andExpect(status().is5xxServerError());

    verifyNoInteractions(questionAndAnswerService);
  }

  @Test
  void shouldSaveQuestionGroupsUnassigningRemovedGroups() throws Exception {
    //
    // given we have pre-existing groups: group1 & group2
    // and we have a pre-existing question assigned to group2
    //
    QandA expectedQuestionGroup1 = new QandA();
    expectedQuestionGroup1.setQuestion(GROUP_TYPE + "-question-group-0");
    expectedQuestionGroup1.setAnswer("group1");
    expectedQuestionGroup1.id(BigDecimal.valueOf(123));

    QandA expectedQuestionGroup2 = new QandA();
    expectedQuestionGroup2.setQuestion(GROUP_TYPE + "-question-group-1");
    expectedQuestionGroup2.setAnswer("group2");
    expectedQuestionGroup2.id(BigDecimal.valueOf(456));

    String question = "Select question group";
    QandA questions = new QandA();
    questions.setQuestion(question);
    questions.setAnswer("group2");
    questions.id(BigDecimal.valueOf(789));

    QandAWithProjectDetails expected = new QandAWithProjectDetails();
    expected.setQandA(List.of(expectedQuestionGroup1, expectedQuestionGroup2, questions));

    when(questionAndAnswerService.getQuestionAndAnswerByEvent(PROC_PROJECT_ID, EVENT_ID, PRINCIPAL)).thenReturn(expected);

    doNothing().when(questionAndAnswerService).deleteSpecificAnswersForGivenQuestion(PRINCIPAL, PROC_PROJECT_ID, EVENT_ID, question, List.of("group2"));

    //
    // when we delete group2
    //
    QandA questionGroup1 = new QandA();
    questionGroup1.setQuestion(GROUP_TYPE + "-question-group-0");
    questionGroup1.setAnswer("group1");

    QuestionGroupNamesWrite request = new QuestionGroupNamesWrite();
    request.setQuestionGroups(List.of("group1"));
    request.setQaIds(List.of(123));

    mockMvc
    .perform(post(EVENTS_PATH + "/{eventID}/question-groups/{groupType}", PROC_PROJECT_ID, EVENT_ID, GROUP_TYPE)
      .with(validJwtReqPostProcessor).accept(APPLICATION_JSON)
      .contentType(MediaType.APPLICATION_JSON)
      .content(objectMapper.writeValueAsString(request)))
    .andDo(print())
    .andExpect(status().isOk())
    .andExpect(content().string(containsString("OK")));

    verify(questionAndAnswerService).getQuestionAndAnswerByEvent(PROC_PROJECT_ID, EVENT_ID, PRINCIPAL);

    verify(questionAndAnswerService, times(1)).deleteQuestionAndAnswerByQaIdFromRepo(PROC_PROJECT_ID, EVENT_ID, 123, PRINCIPAL);
    verify(questionAndAnswerService, times(1)).deleteQuestionAndAnswerByQaIdFromRepo(PROC_PROJECT_ID, EVENT_ID, 456, PRINCIPAL);

    verify(questionAndAnswerService, times(1)).createOrUpdateQuestionAndAnswer(PRINCIPAL, PROC_PROJECT_ID, EVENT_ID, questionGroup1, null);

    //
    // then we expect group2 to be unassigned from the existing questions
    //
    verify(questionAndAnswerService).deleteSpecificAnswersForGivenQuestion(PRINCIPAL, PROC_PROJECT_ID, EVENT_ID, question, List.of("group2"));

    verifyNoMoreInteractions(questionAndAnswerService);
  }
}
