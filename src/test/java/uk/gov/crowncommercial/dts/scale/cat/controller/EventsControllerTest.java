package uk.gov.crowncommercial.dts.scale.cat.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.security.Principal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;
import uk.gov.crowncommercial.dts.scale.cat.exception.JaggaerApplicationException;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.*;
import uk.gov.crowncommercial.dts.scale.cat.service.ProcurementEventService;
import uk.gov.crowncommercial.dts.scale.cat.utils.TendersAPIModelUtils;

/**
 * Controller tests
 */
@WebMvcTest(EventsController.class)
@Import({TendersAPIModelUtils.class})
@ActiveProfiles("test")
class EventsControllerTest {

  private static final String PRINCIPAL = "jsmith@ccs.org.uk";
  private static final Integer PROC_PROJECT_ID = 1;
  private static final String EVENT_ID = "ocds-b5fd17-1";
  private static final String EVENT_NAME = "NAME";
  private static final String JAGGAER_ID = "1";
  private static final EventType EVENT_TYPE = EventType.RFP;
  private static final TenderStatus EVENT_STATUS = TenderStatus.PLANNING;
  private static final String EVENT_STAGE = "Tender";

  private final Tender tender = new Tender();

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @MockBean
  private ProcurementEventService procurementEventService;

  @MockBean
  private Principal principal;

  @MockBean
  private EventSummary eventSummary;

  private final TendersAPIModelUtils tendersAPIModelUtils = new TendersAPIModelUtils();
  private JwtRequestPostProcessor validJwtReqPostProcessor;

  @BeforeEach
  void beforeEach() {
    validJwtReqPostProcessor = jwt().authorities(new SimpleGrantedAuthority("CAT_USER"))
        .jwt(jwt -> jwt.subject(PRINCIPAL));
  }

  @Test
  void createProcurementEvent_200_OK() throws Exception {

    var eventStatus = tendersAPIModelUtils.buildEventStatus(PROC_PROJECT_ID, EVENT_ID, EVENT_NAME,
        JAGGAER_ID, EVENT_TYPE, EVENT_STATUS, EVENT_STAGE);

    when(procurementEventService.createFromTender(eq(PROC_PROJECT_ID), any(Tender.class),
        anyString())).thenReturn(eventStatus);

    mockMvc
        .perform(post("/tenders/projects/" + PROC_PROJECT_ID + "/events")
            .with(validJwtReqPostProcessor).contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(tender)))
        .andDo(print()).andExpect(status().isOk()).andExpect(jsonPath("$.eventID").value(EVENT_ID))
        .andExpect(jsonPath("$.projectID").value(PROC_PROJECT_ID))
        .andExpect(jsonPath("$.name").value(EVENT_NAME))
        .andExpect(jsonPath("$.eventSupportID").value(JAGGAER_ID))
        .andExpect(jsonPath("$.eventType").value(EVENT_TYPE.toString()))
        .andExpect(jsonPath("$.eventStage").value(EVENT_STAGE))
        .andExpect(jsonPath("$.status").value(EVENT_STATUS.toString()));
  }

  @Test
  void createProcurementEvent_401_Unauthorised() throws Exception {
    mockMvc
        .perform(post("/tenders/projects/" + PROC_PROJECT_ID + "/events")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(tender)))
        .andDo(print()).andExpect(status().isUnauthorized())
        .andExpect(content().contentType(APPLICATION_JSON))
        .andExpect(jsonPath("$.errors", hasSize(1)))
        .andExpect(jsonPath("$.errors[0].status", is("401 UNAUTHORIZED")))
        .andExpect(jsonPath("$.errors[0].title", is("Missing, expired or invalid access token")));
  }

  @Test
  void createProcurementEvent_403_Forbidden() throws Exception {
    JwtRequestPostProcessor invalidJwtReqPostProcessor =
        jwt().authorities(new SimpleGrantedAuthority("OTHER")).jwt(jwt -> jwt.subject(PRINCIPAL));

    mockMvc
        .perform(post("/tenders/projects/" + PROC_PROJECT_ID + "/events")
            .with(invalidJwtReqPostProcessor).contentType(APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(tender)))
        .andDo(print()).andExpect(status().isForbidden())
        .andExpect(content().contentType(APPLICATION_JSON))
        .andExpect(jsonPath("$.errors", hasSize(1)))
        .andExpect(jsonPath("$.errors[0].status", is("403 FORBIDDEN")))
        .andExpect(jsonPath("$.errors[0].title", is("Access Denied (Forbidden)")));
  }

  @Test
  void createProcurementEvent_500_ISE() throws Exception {

    when(procurementEventService.createFromTender(PROC_PROJECT_ID, tender, PRINCIPAL))
        .thenThrow(new JaggaerApplicationException("1", "BANG"));

    mockMvc
        .perform(
            post("/tenders/projects/" + PROC_PROJECT_ID + "/events").with(validJwtReqPostProcessor)
                .contentType(APPLICATION_JSON).content(objectMapper.writeValueAsString(tender)))
        .andDo(print()).andExpect(status().isInternalServerError())
        .andExpect(content().contentType(APPLICATION_JSON))
        .andExpect(jsonPath("$.errors", hasSize(1)))
        .andExpect(jsonPath("$.errors[0].status", is("500 INTERNAL_SERVER_ERROR"))).andExpect(
            jsonPath("$.errors[0].title", is("An error occurred invoking an upstream service")));
  }

  @Test
  void updateProcurementEventName_200_OK() throws Exception {

    ProcurementEventName eventName = new ProcurementEventName();
    eventName.setName("New name");

    mockMvc
        .perform(put("/tenders/projects/" + PROC_PROJECT_ID + "/events/" + EVENT_ID + "/name")
            .with(validJwtReqPostProcessor).contentType(APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(eventName)))
        .andDo(print()).andExpect(status().isOk())
        .andExpect(content().contentType(APPLICATION_JSON));

    verify(procurementEventService, times(1)).updateProcurementEventName(PROC_PROJECT_ID, EVENT_ID,
        eventName.getName(), PRINCIPAL);
  }
}
