package uk.gov.crowncommercial.dts.scale.cat.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import uk.gov.crowncommercial.dts.scale.cat.exception.JaggaerApplicationException;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.AgreementDetails;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.ProjectName;
import uk.gov.crowncommercial.dts.scale.cat.service.ProcurementProjectService;
import uk.gov.crowncommercial.dts.scale.cat.utils.TendersAPIModelUtils;

/**
 * Web (mock MVC) Project controller tests. Security aware.
 */
@WebMvcTest(ProjectsController.class)
@ActiveProfiles("test")
class ProjectsControllerTest {

  private static final String PRINCIPAL = "jsmith@ccs.org.uk";
  private static final String CA_NUMBER = "RM1234";
  private static final String LOT_NUMBER = "Lot1a";
  private static final String ORG = "CCS";
  private static final String PROJ_NAME = CA_NUMBER + '-' + LOT_NUMBER + '-' + ORG;
  private static final String EVENT_OCID = "ocds-abc123-1";
  private static final Integer PROC_PROJECT_ID = 1;

  private final AgreementDetails agreementDetails = new AgreementDetails();
  private final TendersAPIModelUtils tendersAPIModelUtils = new TendersAPIModelUtils();

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @MockBean
  private ProcurementProjectService procurementProjectService;
  private JwtRequestPostProcessor validJwtReqPostProcessor;

  @BeforeEach
  void beforeEach() {
    agreementDetails.setAgreementID(CA_NUMBER);
    agreementDetails.setLotID(LOT_NUMBER);

    validJwtReqPostProcessor = jwt().authorities(new SimpleGrantedAuthority("CAT_USER"))
        .jwt(jwt -> jwt.subject(PRINCIPAL));
  }

  @Test
  void createProcurementProject_200_OK() throws Exception {
    var draftProcurementProject = tendersAPIModelUtils
        .buildDraftProcurementProject(agreementDetails, PROC_PROJECT_ID, EVENT_OCID, PROJ_NAME);

    when(procurementProjectService.createFromAgreementDetails(agreementDetails, PRINCIPAL))
        .thenReturn(draftProcurementProject);

    mockMvc
        .perform(post("/tenders/projects/agreements").with(validJwtReqPostProcessor)
            .contentType(APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(agreementDetails)))
        .andDo(print()).andExpect(status().isOk())
        .andExpect(content().contentType(APPLICATION_JSON))
        .andExpect(jsonPath("$.pocurementID").value(PROC_PROJECT_ID))
        .andExpect(jsonPath("$.eventID").value(EVENT_OCID))
        .andExpect(jsonPath("$.defaultName.name").value(PROJ_NAME))
        .andExpect(jsonPath("$.defaultName.components.agreementID").value(CA_NUMBER))
        .andExpect(jsonPath("$.defaultName.components.lotID").value(LOT_NUMBER))
        .andExpect(jsonPath("$.defaultName.components.org").value(ORG));

    verify(procurementProjectService).createFromAgreementDetails(any(AgreementDetails.class),
        anyString());
  }

  @Test
  void createProcurementProject_403_Forbidden() throws Exception {
    JwtRequestPostProcessor invalidJwtReqPostProcessor =
        jwt().authorities(new SimpleGrantedAuthority("OTHER")).jwt(jwt -> jwt.subject(PRINCIPAL));

    mockMvc.perform(post("/tenders/projects/agreements").with(invalidJwtReqPostProcessor)
        .contentType(APPLICATION_JSON).content(objectMapper.writeValueAsString(agreementDetails)))
        .andDo(print()).andExpect(status().isForbidden());
  }

  @Test
  void createProcurementProject_401_Unauthorised() throws Exception {
    mockMvc
        .perform(post("/tenders/projects/agreements").contentType(APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(agreementDetails)))
        .andDo(print()).andExpect(status().isUnauthorized());
  }

  @Test
  void createProcurementProject_500_ISE() throws Exception {

    when(procurementProjectService.createFromAgreementDetails(agreementDetails, PRINCIPAL))
        .thenThrow(new JaggaerApplicationException("1", "BANG"));

    mockMvc
        .perform(post("/tenders/projects/agreements").with(validJwtReqPostProcessor)
            .contentType(APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(agreementDetails)))
        .andDo(print()).andExpect(status().isInternalServerError())
        .andExpect(content().contentType(APPLICATION_JSON))
        .andExpect(jsonPath("$.errors", hasSize(1)))
        .andExpect(jsonPath("$.errors[0].status", is("500 INTERNAL_SERVER_ERROR"))).andExpect(
            jsonPath("$.errors[0].title", is("An error occurred invoking an upstream service")));
  }

  @Test
  void updateProcurementProjectName_200_OK() throws Exception {

    ProjectName projectName = new ProjectName();
    projectName.setName("New name");

    mockMvc
        .perform(post("/tenders/projects/" + PROC_PROJECT_ID + "/name")
            .with(validJwtReqPostProcessor).contentType(APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(projectName)))
        .andDo(print()).andExpect(status().isOk())
        .andExpect(content().contentType(APPLICATION_JSON));

    verify(procurementProjectService, times(1)).updateProcurementProjectName(PROC_PROJECT_ID,
        projectName.getName(), PRINCIPAL);
  }

}
