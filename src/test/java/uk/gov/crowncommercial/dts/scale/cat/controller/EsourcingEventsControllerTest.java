package uk.gov.crowncommercial.dts.scale.cat.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.TEXT_PLAIN;
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
import java.security.Principal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

import org.apache.jena.atlas.logging.Log;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;

import uk.gov.crowncommercial.dts.scale.cat.auth.Authorities;
import uk.gov.crowncommercial.dts.scale.cat.auth.apikey.ApiKeyAuthToken;
import uk.gov.crowncommercial.dts.scale.cat.auth.apikey.ApiKeyDetails;
import uk.gov.crowncommercial.dts.scale.cat.auth.apikey.ApiKeyDetailsProvider;
import uk.gov.crowncommercial.dts.scale.cat.config.ApplicationFlagsConfig;
import uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.exception.JaggaerApplicationException;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementProject;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.*;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.InvalidateEventRequest;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.WorkflowRfxResponse;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;
import uk.gov.crowncommercial.dts.scale.cat.service.DocGenService;
import uk.gov.crowncommercial.dts.scale.cat.service.EventTransitionService;
import uk.gov.crowncommercial.dts.scale.cat.service.JaggaerService;
import uk.gov.crowncommercial.dts.scale.cat.service.ProcurementEventService;
import uk.gov.crowncommercial.dts.scale.cat.service.ValidationService;
import uk.gov.crowncommercial.dts.scale.cat.service.ca.AssessmentScoreExportService;
import uk.gov.crowncommercial.dts.scale.cat.utils.TendersAPIModelUtils;

/**
 * Controller tests
 */
@WebMvcTest(EventsController.class)
@Import({TendersAPIModelUtils.class, JaggaerAPIConfig.class, ApplicationFlagsConfig.class})
@ActiveProfiles("test")
class EsourcingEventsControllerTest {

  private static final String EVENTS_PATH = "/tenders/projects/{procID}/events";
  private static final String TERMINATION_PATH = "/tenders/projects/{procID}/events/{eventID}/termination";
  private static final String MESSAGES_PATH = "messages";
  private static final String PRINCIPAL = "jsmith@ccs.org.uk";
  private static final Integer PROC_PROJECT_ID = 1;
  private static final String EVENT_ID = "ocds-pfhb7i-1";
  private static final String EVENT_NAME = "NAME";
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
  private static final Integer JAGGAER_INVALID_REQUEST_OBJECT_RETURN_CODE = -996;
  private static final String JAGGAER_INVALID_REQUEST_OBJECT_RETURN_MESSAGE =
		  "Invalid Request Object - User is trying to invalidate rfx without the necessary permission or the rfx can't be invalidate;";
  private static final String EXTERNAL_EVENT_ID = "rfq_123456";
  private static final String EXTERNAL_REF_ID = "itt_123345";  
  
  private final TerminationEvent terminationEvent = new TerminationEvent();
  private final ProcurementProject procurementProject = new ProcurementProject();
  private final ProcurementEvent procurementEvent = new ProcurementEvent();
  private final CreateEvent createEvent = new CreateEvent();
  
  private final WorkflowRfxResponse workflowRfxResponse = WorkflowRfxResponse.builder()
  		.returnCode(JAGGAER_INVALID_REQUEST_OBJECT_RETURN_CODE)
  		.returnMessage(JAGGAER_INVALID_REQUEST_OBJECT_RETURN_MESSAGE)
  		.finalStatusCode(JAGGAER_INVALID_REQUEST_OBJECT_RETURN_CODE)
  		.finalStatus(JAGGAER_INVALID_REQUEST_OBJECT_RETURN_MESSAGE)
  		.build();    

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private TendersAPIModelUtils tendersAPIModelUtils;

  @Autowired
  private ObjectMapper objectMapper;

  @MockBean
  private ProcurementEventService procurementEventService;

  @MockBean
  private EventTransitionService eventTransitionService;
  
  @MockBean
  private JaggaerService jaggaerService;
  

  
  @MockBean
  private RetryableTendersDBDelegate retryableTendersDBDelegate;
  
  @MockBean
  private ValidationService validationService;

  @MockBean
  private DocGenService docGenService;

  @MockBean
  private AssessmentScoreExportService exportService;

  @MockBean
  private Principal principal;

  @MockBean
  private EventSummary eventSummary;
  
  @MockBean
  private ApiKeyDetailsProvider apiKeyDetailsProvider;
  
  @Test
  void terminateEvent_401_Unauthorised() throws Exception {
    mockMvc
        .perform(post(TERMINATION_PATH, PROC_PROJECT_ID,EVENT_ID).accept(APPLICATION_JSON)
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
  void terminateEvent_403_Forbidden_JWT() throws Exception {
    var invalidJwtReqPostProcessor =
        jwt().authorities(new SimpleGrantedAuthority("OTHER")).jwt(jwt -> jwt.subject(PRINCIPAL));

    mockMvc
        .perform(post(TERMINATION_PATH, PROC_PROJECT_ID, EVENT_ID).with(invalidJwtReqPostProcessor)
            .accept(APPLICATION_JSON).contentType(APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(createEvent)))
        .andDo(print()).andExpect(status().isForbidden())
        .andExpect(content().contentType(APPLICATION_JSON))
        .andExpect(jsonPath("$.errors", hasSize(1)))
        .andExpect(jsonPath("$.errors[0].status", is("403 FORBIDDEN"))).andExpect(
            jsonPath("$.errors[0].title", is("Access to the requested resource is forbidden")));
  }
  
  @Test
  void terminateEvent_403_Forbidden_APIKEY() throws Exception {

    // provide a valid key but the key deosn't provide the required authorities for this end point
    String key = "jgkepi7df-890g7s-8g7usidfgpoid7yf";
    when(apiKeyDetailsProvider.findDetailsByKey(key)).thenReturn(Optional
        .of(ApiKeyDetails.builder().key(key)
            .authorities(AuthorityUtils.commaSeparatedStringToAuthorityList("OTHER"))
        .build()));
    
    mockMvc
        .perform(post(TERMINATION_PATH, PROC_PROJECT_ID, EVENT_ID)
        	.header("x-api-key", key).accept(APPLICATION_JSON).contentType(APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(createEvent)))
        .andDo(print()).andExpect(status().isForbidden())
        .andExpect(content().contentType(APPLICATION_JSON))
        .andExpect(jsonPath("$.errors", hasSize(1)))
        .andExpect(jsonPath("$.errors[0].status", is("403 FORBIDDEN"))).andExpect(
            jsonPath("$.errors[0].title", is("Access to the requested resource is forbidden")));
    

  }
  
  
  @Test
  void terminateSalesforceEvent_996_Invalid_Response_Object() throws Exception {

    String key = "jgkepi7df-890g7s-8g7usidfgpoid7yf";
    when(apiKeyDetailsProvider.findDetailsByKey(key)).thenReturn(Optional
            .of(ApiKeyDetails.builder().key(key)
                .authorities(AuthorityUtils.commaSeparatedStringToAuthorityList(Authorities.ESOURCING_ROLE))
            .build()));
    
    when(jaggaerService.invalidateSalesforceEvent(any(InvalidateEventRequest.class)))
    	.thenReturn(workflowRfxResponse);
    
    procurementProject.setId(PROC_PROJECT_ID);
    
    procurementEvent.setExternalEventId(EXTERNAL_EVENT_ID);
    procurementEvent.setExternalReferenceId(EXTERNAL_REF_ID);
    procurementEvent.setProject(procurementProject);
    
    when(validationService.validateProjectAndEventIds(PROC_PROJECT_ID, EVENT_ID))
    	.thenReturn(procurementEvent);
    
    when(retryableTendersDBDelegate
    		.findProcurementEventByIdAndOcdsAuthorityNameAndOcidPrefix(anyInt(),anyString(), anyString()))
    	.thenReturn(Optional.of(procurementEvent));
    
    terminationEvent.setTerminationType(TerminationType.CANCELLED);
    
    var response = mockMvc
        .perform(put(TERMINATION_PATH, "123", EVENT_ID)
        	.header("x-api-key", key).accept(APPLICATION_JSON)
        	.contentType(APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(terminationEvent)))
        .andDo(print()).andExpect(status().isOk())
        .andExpect(content().contentType(APPLICATION_JSON));
    

  }

}
