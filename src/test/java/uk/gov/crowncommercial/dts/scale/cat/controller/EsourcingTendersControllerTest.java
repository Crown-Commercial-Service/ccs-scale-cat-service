package uk.gov.crowncommercial.dts.scale.cat.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import uk.gov.crowncommercial.dts.scale.cat.auth.Authorities;
import uk.gov.crowncommercial.dts.scale.cat.auth.apikey.ApiKeyDetails;
import uk.gov.crowncommercial.dts.scale.cat.auth.apikey.ApiKeyDetailsProvider;
import uk.gov.crowncommercial.dts.scale.cat.config.ApiKeyConfig;
import uk.gov.crowncommercial.dts.scale.cat.config.ApplicationFlagsConfig;
import uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.SalesforceProjectTender;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.SalesforceRfx;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;
import uk.gov.crowncommercial.dts.scale.cat.repo.readonly.RfxTemplateMappingRepo;
import uk.gov.crowncommercial.dts.scale.cat.service.ConclaveService;
import uk.gov.crowncommercial.dts.scale.cat.service.ProcurementEventService;
import uk.gov.crowncommercial.dts.scale.cat.service.ProcurementProjectService;
import uk.gov.crowncommercial.dts.scale.cat.service.ProfileManagementService;
import uk.gov.crowncommercial.dts.scale.cat.service.UserProfileService;
import uk.gov.crowncommercial.dts.scale.cat.service.WebclientWrapper;
import uk.gov.crowncommercial.dts.scale.cat.utils.TendersAPIModelUtils;

/**
 * Web (mock MVC) for the ESourcing tenders endpoints.
 */
@WebMvcTest(TendersController.class)
@Import({TendersAPIModelUtils.class, JaggaerAPIConfig.class, GlobalErrorHandler.class,
    ApplicationFlagsConfig.class, ApiKeyConfig.class})
@ActiveProfiles("test")
class EsourcingTendersControllerTest {

  private static final String PRINCIPAL = "peter.simpson@roweit.co.uk";
  private static final String SHORT_DESCRIPTION = "Project for Provision of Face Masks";
  private static final String CA_NUMBER = "RM1234";
  private static final String LOT_NUMBER = "Lot1a";
  private static final String RFX_REFERENCE_CODE = "itt_9899";
  private static final String TENDER_REF_CODE = "project_0001";
  private static final Integer PROC_PROJECT_ID = 1;
  private static final String EVENT_ID = "ocds-pfhb7i-1";

  private static final SalesforceProjectTender salesforceProjectTender = new SalesforceProjectTender();
  private static final SalesforceRfx salesforceRfx = new SalesforceRfx();
 
  @MockBean(answer = Answers.RETURNS_DEEP_STUBS)
  private WebClient jaggaerWebClient;
  
  @Autowired
  private ObjectMapper objectMapper;

  @MockBean
  private UserProfileService userProfileService;

  @MockBean
  private ConclaveService conclaveService;
  
  @MockBean
  private RetryableTendersDBDelegate retryableTendersDBDelegate;
  
  @MockBean
  private RfxTemplateMappingRepo rfxTemplateMappingRepo;

  @MockBean
  private WebclientWrapper webclientWrapper;
  
  @MockBean
  private ProcurementEventService procurementEventService;

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private TendersAPIModelUtils tendersAPIModelUtils;

  @MockBean
  private ProfileManagementService profileManagementService;

  @MockBean
  private ApiKeyDetailsProvider apiKeyDetailsProvider;

  @MockBean
  private ProcurementProjectService procurementProjectService;
  
  @BeforeAll
  static void beforeAll() {
	salesforceProjectTender.setTenderReferenceCode(TENDER_REF_CODE);
	salesforceProjectTender.setSubject(SHORT_DESCRIPTION);
	salesforceProjectTender.setProcurementReference("324233324");
	
	salesforceRfx.setOwnerUserLogin(PRINCIPAL);
	salesforceRfx.setShortDescription(SHORT_DESCRIPTION);
	salesforceRfx.setRfiFlag("0");
	salesforceRfx.setValue("7777");
	salesforceRfx.setQualEnvStatus("0");
	salesforceRfx.setTechEnvStatus("1");	
	salesforceRfx.setCommEnvStatus("1");
	salesforceRfx.setPublishDate("2021-01-28T12:00:00.000+00:00");
	salesforceRfx.setCloseDate("2021-02-27T12:00:00.000+00:00");
	salesforceRfx.setVisibilityEGComments("1");
	salesforceRfx.setRankingStrategy("BEST_TECHNICAL_SCORE");
	salesforceRfx.setProcurementRoute("Open Market");
	salesforceRfx.setFrameworkName("TT3209");
	salesforceRfx.setFrameworkLotNumber(LOT_NUMBER);
	salesforceRfx.setFrameworkRMNumber(CA_NUMBER);
	salesforceProjectTender.setRfx(salesforceRfx);
  }
  
  @Test
  void getProjectDeltas_200_OK() throws Exception {

    String key = "jgkepi7df-890g7s-8g7usidfgpoid7yf";
    when(apiKeyDetailsProvider.findDetailsByKey(key)).thenReturn(Optional
        .of(ApiKeyDetails.builder().key(key)
            .authorities(AuthorityUtils.commaSeparatedStringToAuthorityList(Authorities.ESOURCING_ROLE))
        .build()));

    mockMvc
        .perform(get("/tenders/projects/deltas")
            .queryParam("lastSuccessRun", "2000-10-31T01:30:00.000-05:00")
            .header("x-api-key", key).accept(APPLICATION_JSON))
        .andDo(print()).andExpect(status().isOk())
        .andExpect(content().contentType(APPLICATION_JSON));
  }
  
  @Test
  void getProjectDeltas_403_Forbidden() throws Exception {

    // provide a valid key but the key deosn't provide the required authorities for this end point
    String key = "jgkepi7df-890g7s-8g7usidfgpoid7yf";
    when(apiKeyDetailsProvider.findDetailsByKey(key)).thenReturn(Optional
        .of(ApiKeyDetails.builder().key(key)
            .authorities(AuthorityUtils.commaSeparatedStringToAuthorityList(Authorities.CAT_ADMINISTRATOR_ROLE))
        .build()));

    mockMvc
        .perform(get("/tenders/projects/deltas")
            .queryParam("lastSuccessRun", "2000-10-31T01:30:00.000-05:00")
            .header("x-api-key", key).accept(APPLICATION_JSON))
        .andDo(print()).andExpect(status().isForbidden())
        .andExpect(content().contentType(APPLICATION_JSON));
  }
  
  @Test
  void getProjectDeltas_401_Unauthorized() throws Exception {
    
    // provide an invalid key
    String key = "jgkepi7df-890g7s-8g7usidfgpoid7yf";
    when(apiKeyDetailsProvider.findDetailsByKey(key)).thenReturn(Optional.empty());

    mockMvc
        .perform(get("/tenders/projects/deltas")
            .queryParam("lastSuccessRun", "2000-10-31T01:30:00.000-05:00")
            .header("x-api-key", key).accept(APPLICATION_JSON)
            )
        .andDo(print()).andExpect(status().isUnauthorized())
        .andExpect(content().contentType(APPLICATION_JSON));
  }
  
  @Test
  void createProjectFromSalesforce_200_OK() throws Exception {

	String key = "jgkepi7df-890g7s-8g7usidfgpoid7yf";
    when(apiKeyDetailsProvider.findDetailsByKey(key)).thenReturn(Optional
            .of(ApiKeyDetails.builder().key(key)
                .authorities(AuthorityUtils.commaSeparatedStringToAuthorityList(Authorities.ESOURCING_ROLE))
            .build()));
    
	var salesforceProjectTender200Response =  
			tendersAPIModelUtils.buildSalesforceProjectTender200Response(TENDER_REF_CODE,RFX_REFERENCE_CODE,EVENT_ID,PROC_PROJECT_ID);

    when(procurementProjectService.createFromSalesforceDetails(salesforceProjectTender))
    	.thenReturn(salesforceProjectTender200Response);

    mockMvc
        .perform(post("/tenders/projects/salesforce")
            .header("x-api-key", key)
            .contentType(APPLICATION_JSON)
           .content(objectMapper.writeValueAsString(salesforceProjectTender)))
        .andDo(print()).andExpect(status().isOk())
        .andExpect(content().contentType(APPLICATION_JSON))
        .andExpect(jsonPath("$.tenderReferenceCode").value(TENDER_REF_CODE))
        .andExpect(jsonPath("$.rfxReferenceCode").value(RFX_REFERENCE_CODE));

        verify(procurementProjectService).createFromSalesforceDetails(any(SalesforceProjectTender.class));
  }
  
}
