package uk.gov.crowncommercial.dts.scale.cat.controller;

import static org.hamcrest.Matchers.contains;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.crowncommercial.dts.scale.cat.service.ProcurementProjectService;

/**
 * Web (mock MVC) Tenders controller tests. Security aware.
 */
@WebMvcTest(TendersController.class)
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
        .andDo(print()).andExpect(status().isForbidden());
  }

  @Test
  void listProcurementEventTypes_401_Unauthorised() throws Exception {
    mockMvc.perform(get("/tenders/event-types").accept(APPLICATION_JSON)).andDo(print())
        .andExpect(status().isUnauthorized());
  }

}
