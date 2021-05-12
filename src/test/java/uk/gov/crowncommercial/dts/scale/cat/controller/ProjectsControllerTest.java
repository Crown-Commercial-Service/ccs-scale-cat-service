package uk.gov.crowncommercial.dts.scale.cat.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.AgreementDetails;
import uk.gov.crowncommercial.dts.scale.cat.service.ProcurementProjectService;
import uk.gov.crowncommercial.dts.scale.cat.utils.TendersAPIModelUtils;

/**
 *
 */
@WebMvcTest(controllers = ProjectsController.class)
@ActiveProfiles("test")
class ProjectsControllerTest {

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

  @BeforeEach
  void beforeEach() {
    agreementDetails.setAgreementID(CA_NUMBER);
    agreementDetails.setLotID(LOT_NUMBER);
  }

  @Test
  void createProcurementProject_200_OK() throws Exception {
    var draftProcurementProject = tendersAPIModelUtils
        .buildDraftProcurementProject(agreementDetails, PROC_PROJECT_ID, EVENT_OCID, PROJ_NAME);

    when(procurementProjectService.createFromAgreementDetails(any(AgreementDetails.class),
        anyString())).thenReturn(draftProcurementProject);

    mockMvc
        .perform(
            post("/tenders/projects/agreements").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(agreementDetails)))
        .andDo(print()).andExpect(status().isOk())
        .andExpect(jsonPath("$.pocurementID").value(PROC_PROJECT_ID))
        .andExpect(jsonPath("$.eventID").value(EVENT_OCID))
        .andExpect(jsonPath("$.defaultName.name").value(PROJ_NAME))
        .andExpect(jsonPath("$.defaultName.components.agreementID").value(CA_NUMBER))
        .andExpect(jsonPath("$.defaultName.components.lotID").value(LOT_NUMBER))
        .andExpect(jsonPath("$.defaultName.components.org").value(ORG));
  }

  @Test
  void createProcurementProject_401_Unauthorised() throws Exception {
    mockMvc
        .perform(post("/tenders/projects/agreements").contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(agreementDetails)))
        .andDo(print()).andExpect(status().is(401));
  }

}
