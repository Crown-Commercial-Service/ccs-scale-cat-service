package uk.gov.crowncommercial.dts.scale.cat.controller;

import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.crowncommercial.dts.scale.cat.auth.Authorities;
import uk.gov.crowncommercial.dts.scale.cat.auth.apikey.ApiKeyDetails;
import uk.gov.crowncommercial.dts.scale.cat.auth.apikey.ApiKeyDetailsProvider;
import uk.gov.crowncommercial.dts.scale.cat.config.ApiKeyConfig;
import uk.gov.crowncommercial.dts.scale.cat.config.ApplicationFlagsConfig;
import uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.service.ProfileManagementService;
import uk.gov.crowncommercial.dts.scale.cat.utils.TendersAPIModelUtils;

/**
 * Web (mock MVC) for the ESourcing tenders endpoints.
 */
@WebMvcTest(TendersController.class)
@Import({TendersAPIModelUtils.class, JaggaerAPIConfig.class, GlobalErrorHandler.class,
    ApplicationFlagsConfig.class, ApiKeyConfig.class})
@ActiveProfiles("test")
class EsourcingTendersControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private ProfileManagementService profileManagementService;

  @MockBean
  private ApiKeyDetailsProvider apiKeyDetailsProvider;

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

}
