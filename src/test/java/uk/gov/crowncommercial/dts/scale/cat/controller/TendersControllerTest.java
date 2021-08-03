package uk.gov.crowncommercial.dts.scale.cat.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.crowncommercial.dts.scale.cat.service.ProcurementProjectService;
import uk.gov.crowncommercial.dts.scale.cat.utils.TendersAPIModelUtils;

/**
 * Web (mock MVC) Tenders controller tests. Security aware.
 */
@WebMvcTest(TendersController.class)
@Import({TendersAPIModelUtils.class})
@ActiveProfiles("test")
class TendersControllerTest {

  private static final String PRINCIPAL = "jsmith@ccs.org.uk";

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private ProcurementProjectService procurementProjectService;
  private JwtRequestPostProcessor validJwtReqPostProcessor;

  @BeforeEach
  void beforeEach() {
    validJwtReqPostProcessor = jwt().authorities(new SimpleGrantedAuthority("CAT_USER"))
        .jwt(jwt -> jwt.subject(PRINCIPAL));
  }

  @Test
  void listProcurementEventTypes_200_OK() throws Exception {
    mockMvc
        .perform(
            get("/tenders/event-types").with(validJwtReqPostProcessor).accept(APPLICATION_JSON))
        .andDo(print()).andExpect(status().isOk())
        .andExpect(content().contentType(APPLICATION_JSON)).andExpect(jsonPath("$.size()").value(5))
        .andExpect(jsonPath("$[*]", contains("EOI", "RFI", "RFP", "DA", "SL")));
  }

  @Test
  void listProcurementEventTypes_403_Forbidden() throws Exception {
    JwtRequestPostProcessor invalidJwtReqPostProcessor =
        jwt().authorities(new SimpleGrantedAuthority("OTHER")).jwt(jwt -> jwt.subject(PRINCIPAL));

    mockMvc
        .perform(
            get("/tenders/event-types").with(invalidJwtReqPostProcessor).accept(APPLICATION_JSON))
        .andDo(print()).andExpect(status().isForbidden())
        .andExpect(content().contentType(APPLICATION_JSON))
        .andExpect(jsonPath("$.errors", hasSize(1)))
        .andExpect(jsonPath("$.errors[0].status", is("403 FORBIDDEN"))).andExpect(
            jsonPath("$.errors[0].title", is("Access to the requested resource is forbidden")));
  }

  @Test
  void listProcurementEventTypes_401_Unauthorised() throws Exception {
    mockMvc.perform(get("/tenders/event-types").accept(APPLICATION_JSON)).andDo(print())
        .andExpect(status().isUnauthorized()).andExpect(content().contentType(APPLICATION_JSON))
        .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")))
        .andExpect(jsonPath("$.errors", hasSize(1)))
        .andExpect(jsonPath("$.errors[0].status", is("401 UNAUTHORIZED")))
        .andExpect(jsonPath("$.errors[0].title", is("Missing, expired or invalid access token")));
  }

}
