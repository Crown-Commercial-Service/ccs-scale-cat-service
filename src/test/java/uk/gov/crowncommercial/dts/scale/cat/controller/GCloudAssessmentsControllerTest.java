package uk.gov.crowncommercial.dts.scale.cat.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.javacrumbs.shedlock.core.LockProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.crowncommercial.dts.scale.cat.config.ApplicationFlagsConfig;
import uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.config.OAuth2Config;
import uk.gov.crowncommercial.dts.scale.cat.model.assessment.GCloudEProcurement;
import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.GCloudAssessment;
import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.GCloudResult;
import uk.gov.crowncommercial.dts.scale.cat.service.AgreementsService;
import uk.gov.crowncommercial.dts.scale.cat.service.ca.AssessmentService;
import uk.gov.crowncommercial.dts.scale.cat.service.ca.GCloudAssessmentService;
import uk.gov.crowncommercial.dts.scale.cat.utils.TendersAPIModelUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;

import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@WebMvcTest(GCloudAssessmentsController.class)
@Import({TendersAPIModelUtils.class, JaggaerAPIConfig.class, ApplicationFlagsConfig.class, OAuth2Config.class})
@ActiveProfiles("test")
public class GCloudAssessmentsControllerTest {
    private static final String ASSESSMENTS_PATH = "/assessments";
    private static final String GCLOUD_ASSESSMENTS_PATH = "/assessments/gcloud";
    private static final Integer ASSESSMENT_ID = 1;
    private static final String EXTERNAL_TOOL_ID = "2";
    private static final String SERVICE_NAME = "Physical and Environmental Security";
    private static final Integer TOOL_ID = 2;
    private static final String PRINCIPAL = "jsmith@ccs.org.uk";
    private static final String GCLOUD_E_PROCUREMENT_PATH = ASSESSMENTS_PATH + "/gcloud/eprocurement";

    @MockitoBean
    private GCloudAssessmentService assessmentService;

    @MockitoBean
    private AssessmentService coreAssessmentService;

    @MockitoBean
    private AgreementsService agreementsService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MockMvc mockMvc;
    
    @MockitoBean
    private LockProvider lockProvider;

    private SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor validJwtReqPostProcessor;

    @BeforeEach
    void beforeEach() {
        validJwtReqPostProcessor = jwt().authorities(new SimpleGrantedAuthority("CAT_USER"))
                .jwt(jwt -> jwt.subject(PRINCIPAL));
    }

    @Test
    void getGcloudAssessment_200_OK() throws Exception {
        GCloudAssessment assessment = new GCloudAssessment();
        assessment.setAssessmentId(ASSESSMENT_ID);
        assessment.setExternalToolId(EXTERNAL_TOOL_ID);

        GCloudResult result = new GCloudResult();
        result.setServiceName(SERVICE_NAME);
        ArrayList<GCloudResult> resultList = new ArrayList<>();
        resultList.add(result);
        assessment.setResults(resultList);

        when(assessmentService.getGcloudAssessment(ASSESSMENT_ID)).thenReturn(assessment);

        mockMvc
                .perform(get(ASSESSMENTS_PATH + "/{assessmentID}/gcloud", ASSESSMENT_ID)
                        .with(validJwtReqPostProcessor).accept(APPLICATION_JSON))
                .andDo(print()).andExpect(status().isOk())
                .andExpect(content().contentType(APPLICATION_JSON))
                .andExpect(jsonPath("$.assessment-id").value(ASSESSMENT_ID))
                .andExpect(jsonPath("$.external-tool-id").value(TOOL_ID))
                .andExpect(jsonPath("$.results[0].serviceName").value(SERVICE_NAME));
    }

    @Test
    void createGcloudAssessment_200_OK() throws Exception {
        GCloudAssessment assessment = new GCloudAssessment();

        when(assessmentService.createGcloudAssessment(assessment, PRINCIPAL)).thenReturn(1);

        mockMvc
                .perform(post(GCLOUD_ASSESSMENTS_PATH).with(validJwtReqPostProcessor).contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(assessment)))
                .andDo(print()).andExpect(status().isOk())
                .andExpect(content().contentType(APPLICATION_JSON)).andExpect(jsonPath("$").value(1));

        verify(assessmentService, times(1)).createGcloudAssessment(assessment, PRINCIPAL);
    }

    @Test
    void updateGcloudAssessment_200_OK() throws Exception {
        GCloudAssessment assessment = new GCloudAssessment();
        assessment.setAssessmentId(ASSESSMENT_ID);

        mockMvc
                .perform(put(ASSESSMENTS_PATH + "/{assessmentID}/gcloud", ASSESSMENT_ID)
                        .with(validJwtReqPostProcessor).contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(assessment)))
                .andDo(print()).andExpect(status().isOk());

        verify(assessmentService, times(1)).updateGcloudAssessment(assessment, ASSESSMENT_ID, PRINCIPAL);
    }

    @Test
    void deleteGcloudAssessment_200_OK() throws Exception {
        mockMvc
                .perform(delete(ASSESSMENTS_PATH + "/{assessmentID}", ASSESSMENT_ID)
                        .with(validJwtReqPostProcessor).accept(APPLICATION_JSON))
                .andDo(print()).andExpect(status().isOk());

        verify(assessmentService, times(1)).deleteGcloudAssessment(ASSESSMENT_ID);
    }

    @Test
    void getGcloudEProcurementDetailsReturns200_WhenRecordExists() throws Exception {
        when(assessmentService.getGcloudEProcurement(anyString())).thenReturn(givenValidGCloudEProcurement());

        mockMvc.perform(get(GCLOUD_E_PROCUREMENT_PATH)
                .with(validJwtReqPostProcessor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectName").value("Test Project"));
    }

    @Test
    void  getGcloudEProcurementDetailsReturns401_WhenNotAuthenticated() throws Exception {
        // No .with(jwt()) post-processor used here
        mockMvc.perform(get(GCLOUD_E_PROCUREMENT_PATH))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void addGcloudEProcurementDetailsReturns200_WhenDataIsValid() throws Exception {
        when(assessmentService.createGcloudEProcurement(any(), anyString())).thenReturn(1);

        mockMvc.perform(post(GCLOUD_E_PROCUREMENT_PATH)
                        .with(validJwtReqPostProcessor)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(givenValidGCloudEProcurement())))
                .andExpect(status().isOk())
                .andExpect(content().string("1"));
    }

    @Test
    void addGcloudEProcurementDetailsReturns400_WhenJsonIsMalformed() throws Exception {
        String malformedJson = "{ \"assessmentId\": \"NOT_A_NUMBER\" }";

        mockMvc.perform(post(GCLOUD_E_PROCUREMENT_PATH)
                        .with(validJwtReqPostProcessor)
                        .contentType(APPLICATION_JSON)
                        .content(malformedJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addDetails_ReturnsZero_WhenServiceThrowsException() throws Exception {

        when(assessmentService.createGcloudEProcurement(any(), anyString()))
                .thenThrow(new RuntimeException("Database down"));

        mockMvc.perform(post(GCLOUD_E_PROCUREMENT_PATH)
                        .with(validJwtReqPostProcessor)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(givenValidGCloudEProcurement())))
                .andExpect(status().isOk()) // It remains 200 because of the try-catch block
                .andExpect(content().string("0"));
    }

    private GCloudEProcurement givenValidGCloudEProcurement() {
        return GCloudEProcurement.builder()
                .assessmentId(1)
                .projectName("Test Project")
                .contractStartDate(LocalDate.now())
                .estimatedContractValue(BigDecimal.TEN)
                .build();
    }
}
