package uk.gov.crowncommercial.dts.scale.cat.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
import java.util.Arrays;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.exception.JaggaerApplicationException;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.AgreementDetails;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.ProcurementProjectName;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.UpdateTeamMember;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.UpdateTeamMemberType;
import uk.gov.crowncommercial.dts.scale.cat.service.ProcurementProjectService;
import uk.gov.crowncommercial.dts.scale.cat.util.TestUtils;
import uk.gov.crowncommercial.dts.scale.cat.utils.TendersAPIModelUtils;

/**
 * Web (mock MVC) Project controller tests. Security aware.
 */
@WebMvcTest(ProjectsController.class)
@Import({TendersAPIModelUtils.class, JaggaerAPIConfig.class})
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

  @Autowired
  private TendersAPIModelUtils tendersAPIModelUtils;

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @MockBean
  private ProcurementProjectService procurementProjectService;
  private JwtRequestPostProcessor validJwtReqPostProcessor;

  @BeforeEach
  void beforeEach() {
    agreementDetails.setAgreementId(CA_NUMBER);
    agreementDetails.setLotId(LOT_NUMBER);

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
        .andExpect(jsonPath("$.procurementID").value(PROC_PROJECT_ID))
        .andExpect(jsonPath("$.eventId").value(EVENT_OCID))
        .andExpect(jsonPath("$.defaultName.name").value(PROJ_NAME))
        .andExpect(jsonPath("$.defaultName.components.agreementId").value(CA_NUMBER))
        .andExpect(jsonPath("$.defaultName.components.lotId").value(LOT_NUMBER))
        .andExpect(jsonPath("$.defaultName.components.org").value(ORG));

    verify(procurementProjectService).createFromAgreementDetails(any(AgreementDetails.class),
        anyString());
  }

  @Test
  void createProcurementProject_403_Forbidden() throws Exception {
    var invalidJwtReqPostProcessor =
        jwt().authorities(new SimpleGrantedAuthority("OTHER")).jwt(jwt -> jwt.subject(PRINCIPAL));

    mockMvc
        .perform(post("/tenders/projects/agreements").with(invalidJwtReqPostProcessor)
            .contentType(APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(agreementDetails)))
        .andDo(print()).andExpect(status().isForbidden())
        .andExpect(content().contentType(APPLICATION_JSON))
        .andExpect(jsonPath("$.errors", hasSize(1)))
        .andExpect(jsonPath("$.errors[0].status", is("403 FORBIDDEN"))).andExpect(
            jsonPath("$.errors[0].title", is("Access to the requested resource is forbidden")));
  }

  @Test
  void createProcurementProject_401_Unauthorised() throws Exception {
    mockMvc
        .perform(post("/tenders/projects/agreements").contentType(APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(agreementDetails)))
        .andDo(print()).andExpect(status().isUnauthorized())
        .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")))
        .andExpect(content().contentType(APPLICATION_JSON))
        .andExpect(jsonPath("$.errors", hasSize(1)))
        .andExpect(jsonPath("$.errors[0].status", is("401 UNAUTHORIZED")))
        .andExpect(jsonPath("$.errors[0].title", is("Missing, expired or invalid access token")));
  }

  @Test
  void createProcurementProject_401_Forbidden_Invalid_JWT() throws Exception {
    // No subject claim
    var invalidJwtReqPostProcessor = jwt().authorities(new SimpleGrantedAuthority("CAT_USER"))
        .jwt(jwt -> jwt.claims(claims -> claims.remove("sub")));

    mockMvc
        .perform(post("/tenders/projects/agreements").with(invalidJwtReqPostProcessor)
            .contentType(APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(agreementDetails)))
        .andDo(print()).andExpect(status().isUnauthorized())
        .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")))
        .andExpect(content().contentType(APPLICATION_JSON))
        .andExpect(jsonPath("$.errors", hasSize(1)))
        .andExpect(jsonPath("$.errors[0].status", is("401 UNAUTHORIZED")))
        .andExpect(jsonPath("$.errors[0].title", is("Missing, expired or invalid access token")));
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

    var projectName = new ProcurementProjectName();
    projectName.setName("New name");

    mockMvc
        .perform(put("/tenders/projects/" + PROC_PROJECT_ID + "/name")
            .with(validJwtReqPostProcessor).contentType(APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(projectName)))
        .andDo(print()).andExpect(status().isOk())
        .andExpect(content().contentType(APPLICATION_JSON));

    verify(procurementProjectService, times(1)).updateProcurementProjectName(PROC_PROJECT_ID,
        projectName.getName(), PRINCIPAL);
  }

  /*
   * TODO: This will be refactored to once filtering of event types per project is in place
   */
  @Test
  void listProcurementEventTypes_200_OK() throws Exception {

    var expectedJson = "[" + "{\"type\":\"EOI\",\"description\":\"Expression of Interest\","
        + "\"preMarketActivity\":true},"
        + "{\"type\":\"RFI\",\"description\":\"Request for Information\","
        + "\"preMarketActivity\":true},"
        + "{\"type\":\"CA\",\"description\":\"Capability Assessment\",\"preMarketActivity\":true},"
        + "{\"type\":\"DA\",\"description\":\"Direct Award\",\"preMarketActivity\":true},"
        + "{\"type\":\"FC\",\"description\":\"Further Competition\",\"preMarketActivity\":true}]";

    when(procurementProjectService.getProjectEventTypes(PROC_PROJECT_ID))
        .thenReturn(TestUtils.getEventTypes());

    mockMvc
        .perform(get("/tenders/projects/" + PROC_PROJECT_ID + "/event-types")
            .with(validJwtReqPostProcessor).accept(APPLICATION_JSON))
        .andDo(print()).andExpect(status().isOk())
        .andExpect(content().contentType(APPLICATION_JSON)).andExpect(jsonPath("$.size()").value(5))
        .andExpect(content().string(expectedJson));
  }

  @Test
  void getProjectUsers_200_OK() throws Exception {
    ;

    when(procurementProjectService.getProjectTeamMembers(PROC_PROJECT_ID))
        .thenReturn(Arrays.asList(TestUtils.getTeamMember()));

    mockMvc
        .perform(get("/tenders/projects/" + PROC_PROJECT_ID + "/users")
            .with(validJwtReqPostProcessor).accept(APPLICATION_JSON))
        .andDo(print()).andExpect(status().isOk())
        .andExpect(content().contentType(APPLICATION_JSON)).andExpect(jsonPath("$.size()").value(1))
        .andExpect(jsonPath("$[0].OCDS.id", is(TestUtils.USERID)))
        .andExpect(jsonPath("$[0].OCDS.contact.name", is(TestUtils.USER_NAME)))
        .andExpect(jsonPath("$[0].OCDS.contact.email", is(TestUtils.USERID)))
        .andExpect(jsonPath("$[0].nonOCDS.teamMember", is(true)))
        .andExpect(jsonPath("$[0].nonOCDS.emailRecipient", is(false)))
        .andExpect(jsonPath("$[0].nonOCDS.projectOwner", is(false)));
  }

  @Test
  void getProjectUsers_401_Forbidden_Invalid_JWT() throws Exception {
    // No subject claim
    var invalidJwtReqPostProcessor = jwt().authorities(new SimpleGrantedAuthority("CAT_USER"))
        .jwt(jwt -> jwt.claims(claims -> claims.remove("sub")));

    mockMvc
        .perform(get("/tenders/projects/" + PROC_PROJECT_ID + "/users")
            .with(invalidJwtReqPostProcessor).contentType(APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(agreementDetails)))
        .andDo(print()).andExpect(status().isUnauthorized())
        .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")))
        .andExpect(content().contentType(APPLICATION_JSON))
        .andExpect(jsonPath("$.errors", hasSize(1)))
        .andExpect(jsonPath("$.errors[0].status", is("401 UNAUTHORIZED")))
        .andExpect(jsonPath("$.errors[0].title", is("Missing, expired or invalid access token")));
  }

  @Test
  void getProjectUsers_500_ISE() throws Exception {

    when(procurementProjectService.getProjectTeamMembers(PROC_PROJECT_ID))
        .thenThrow(new JaggaerApplicationException("1", "BANG"));

    mockMvc
        .perform(get("/tenders/projects/" + PROC_PROJECT_ID + "/users")
            .with(validJwtReqPostProcessor).contentType(APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(agreementDetails)))
        .andDo(print()).andExpect(status().isInternalServerError())
        .andExpect(content().contentType(APPLICATION_JSON))
        .andExpect(jsonPath("$.errors", hasSize(1)))
        .andExpect(jsonPath("$.errors[0].status", is("500 INTERNAL_SERVER_ERROR"))).andExpect(
            jsonPath("$.errors[0].title", is("An error occurred invoking an upstream service")));
  }

  @Test
  void addProjectUser_200_OK() throws Exception {

    UpdateTeamMember updateTeamMember = new UpdateTeamMember();
    updateTeamMember.setUserType(UpdateTeamMemberType.TEAM_MEMBER);

    when(procurementProjectService.addProjectTeamMember(PROC_PROJECT_ID, TestUtils.USERID,
        updateTeamMember)).thenReturn(TestUtils.getTeamMember());

    mockMvc
        .perform(put("/tenders/projects/" + PROC_PROJECT_ID + "/users/" + TestUtils.USERID)
            .with(validJwtReqPostProcessor).contentType(APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(updateTeamMember)))
        .andDo(print()).andExpect(status().isOk())
        .andExpect(content().contentType(APPLICATION_JSON))
        .andExpect(jsonPath("$.OCDS.id", is(TestUtils.USERID)))
        .andExpect(jsonPath("$.OCDS.contact.name", is(TestUtils.USER_NAME)))
        .andExpect(jsonPath("$.OCDS.contact.email", is(TestUtils.USERID)))
        .andExpect(jsonPath("$.nonOCDS.teamMember", is(true)))
        .andExpect(jsonPath("$.nonOCDS.emailRecipient", is(false)))
        .andExpect(jsonPath("$.nonOCDS.projectOwner", is(false)));

    verify(procurementProjectService).addProjectTeamMember(PROC_PROJECT_ID, TestUtils.USERID,
        updateTeamMember);
  }

  @Test
  void addProjectUser_401_Forbidden_Invalid_JWT() throws Exception {
    // No subject claim
    var invalidJwtReqPostProcessor = jwt().authorities(new SimpleGrantedAuthority("CAT_USER"))
        .jwt(jwt -> jwt.claims(claims -> claims.remove("sub")));

    mockMvc
        .perform(put("/tenders/projects/" + PROC_PROJECT_ID + "/users/" + TestUtils.USERID)
            .with(invalidJwtReqPostProcessor).contentType(APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(agreementDetails)))
        .andDo(print()).andExpect(status().isUnauthorized())
        .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")))
        .andExpect(content().contentType(APPLICATION_JSON))
        .andExpect(jsonPath("$.errors", hasSize(1)))
        .andExpect(jsonPath("$.errors[0].status", is("401 UNAUTHORIZED")))
        .andExpect(jsonPath("$.errors[0].title", is("Missing, expired or invalid access token")));
  }

  @Test
  void addProjectUser_500_ISE() throws Exception {

    UpdateTeamMember updateTeamMember = new UpdateTeamMember();
    updateTeamMember.setUserType(UpdateTeamMemberType.TEAM_MEMBER);

    when(procurementProjectService.addProjectTeamMember(PROC_PROJECT_ID, TestUtils.USERID,
        updateTeamMember)).thenThrow(new JaggaerApplicationException("1", "BANG"));

    mockMvc
        .perform(put("/tenders/projects/" + PROC_PROJECT_ID + "/users/" + TestUtils.USERID)
            .with(validJwtReqPostProcessor).contentType(APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(updateTeamMember)))
        .andDo(print()).andExpect(status().isInternalServerError())
        .andExpect(content().contentType(APPLICATION_JSON))
        .andExpect(jsonPath("$.errors", hasSize(1)))
        .andExpect(jsonPath("$.errors[0].status", is("500 INTERNAL_SERVER_ERROR"))).andExpect(
            jsonPath("$.errors[0].title", is("An error occurred invoking an upstream service")));
  }

}

