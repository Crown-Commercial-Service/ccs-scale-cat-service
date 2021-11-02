package uk.gov.crowncommercial.dts.scale.cat.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.security.Principal;
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
import uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.exception.JaggaerApplicationException;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.*;
import uk.gov.crowncommercial.dts.scale.cat.service.ProcurementEventService;
import uk.gov.crowncommercial.dts.scale.cat.utils.TendersAPIModelUtils;

/**
 * Controller tests
 */
@WebMvcTest(EventsController.class)
@Import({TendersAPIModelUtils.class, JaggaerAPIConfig.class})
@ActiveProfiles("test")
class EventsControllerTest {

  private static final String EVENTS_PATH = "/tenders/projects/{procID}/events";
  private static final String PRINCIPAL = "jsmith@ccs.org.uk";
  private static final Integer PROC_PROJECT_ID = 1;
  private static final String EVENT_ID = "ocds-b5fd17-1";
  private static final String EVENT_NAME = "NAME";
  private static final String JAGGAER_ID = "1";
  private static final ViewEventType EVENT_TYPE = ViewEventType.TBD;
  private static final TenderStatus EVENT_STATUS = TenderStatus.PLANNING;
  private static final ReleaseTag EVENT_STAGE = ReleaseTag.TENDER;

  private final CreateEvent createEvent = new CreateEvent();

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private TendersAPIModelUtils tendersAPIModelUtils;

  @Autowired
  private ObjectMapper objectMapper;

  @MockBean
  private ProcurementEventService procurementEventService;

  @MockBean
  private Principal principal;

  @MockBean
  private EventSummary eventSummary;

  private JwtRequestPostProcessor validJwtReqPostProcessor;

  @BeforeEach
  void beforeEach() {
    validJwtReqPostProcessor = jwt().authorities(new SimpleGrantedAuthority("CAT_USER"))
        .jwt(jwt -> jwt.subject(PRINCIPAL));
  }

  @Test
  void createProcurementEvent_200_OK() throws Exception {

    var eventStatus = tendersAPIModelUtils.buildEventSummary(EVENT_ID, EVENT_NAME, JAGGAER_ID,
        EVENT_TYPE, EVENT_STATUS, EVENT_STAGE);

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

    mockMvc
        .perform(put(EVENTS_PATH + "/{eventID}", PROC_PROJECT_ID, EVENT_ID)
            .with(validJwtReqPostProcessor).contentType(APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(updateEvent)))
        .andDo(print()).andExpect(status().isOk())
        .andExpect(content().contentType(APPLICATION_JSON));

    verify(procurementEventService, times(1)).updateProcurementEvent(PROC_PROJECT_ID, EVENT_ID,
        updateEvent, PRINCIPAL);
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
}
