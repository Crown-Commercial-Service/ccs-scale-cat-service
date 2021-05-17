package uk.gov.crowncommercial.dts.scale.cat.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.security.Principal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.EventSummary;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.EventType;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.Tender;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.TenderStatus;
import uk.gov.crowncommercial.dts.scale.cat.service.ProcurementEventService;
import uk.gov.crowncommercial.dts.scale.cat.utils.TendersAPIModelUtils;

/**
 * Controller tests
 */
@WebMvcTest(controllers = EventsController.class)
@ActiveProfiles("test")
class EventsControllerTest {

  private static final String PRINCIPAL = "jsmith@ccs.org.uk";
  private static final Integer PROC_PROJECT_ID = 1;
  private static final String EVENT_ID = "1";
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
    // TODO: Update to the correct roles value once Auth service fixed (should be proper list or
    // space-separated string)
    validJwtReqPostProcessor =
        jwt().authorities(new SimpleGrantedAuthority("[\"CAT_ADMINISTRATOR\",\"CAT_USER\"]"))
            .jwt(jwt -> jwt.subject(PRINCIPAL));
  }

  @Test
  void createProcurementEvent_200_OK() throws Exception {

    var eventSummary = tendersAPIModelUtils.buildEventSummary(EVENT_ID, EVENT_NAME, JAGGAER_ID,
        EVENT_TYPE, EVENT_STATUS, EVENT_STAGE);

    when(procurementEventService.createFromTender(anyInt(), any(Tender.class), anyString()))
        .thenReturn(eventSummary);

    mockMvc
        .perform(post("/tenders/projects/" + PROC_PROJECT_ID + "/events")
            .with(validJwtReqPostProcessor).contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(tender)))
        .andDo(print()).andExpect(status().isOk()).andExpect(jsonPath("$.eventID").value(EVENT_ID))
        .andExpect(jsonPath("$.name").value(EVENT_NAME))
        .andExpect(jsonPath("$.eventSupportID").value(JAGGAER_ID))
        .andExpect(jsonPath("$.eventType").value(EVENT_TYPE.toString()))
        .andExpect(jsonPath("$.eventStage").value(EVENT_STAGE))
        .andExpect(jsonPath("$.status").value(EVENT_STATUS.toString()));
  }

  @Test
  void createProcurementEvent_401_Unauthorised() throws Exception {
    mockMvc.perform(post("/tenders/projects/" + PROC_PROJECT_ID + "/events")
        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(tender)))
        .andDo(print()).andExpect(status().is(401));
  }
}
