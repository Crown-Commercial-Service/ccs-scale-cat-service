package uk.gov.crowncommercial.dts.scale.cat.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.crowncommercial.dts.scale.cat.config.ApplicationFlagsConfig;
import uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.config.OAuth2Config;
import uk.gov.crowncommercial.dts.scale.cat.model.assessment.MiQuestionAnswer;
import uk.gov.crowncommercial.dts.scale.cat.service.AgreementsService;
import uk.gov.crowncommercial.dts.scale.cat.service.MiService;
import uk.gov.crowncommercial.dts.scale.cat.service.ca.AssessmentService;
import uk.gov.crowncommercial.dts.scale.cat.utils.TendersAPIModelUtils;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MiController.class)
@Import({TendersAPIModelUtils.class, JaggaerAPIConfig.class, ApplicationFlagsConfig.class, OAuth2Config.class})
@ActiveProfiles("test")
class MiControllerTest {

    private static final String MI_PATH = "/eprocurement/mi";
    private static final String PRINCIPAL = "jsmith@ccs.org.uk";

    @MockitoBean
    private MiService miService;

    @MockitoBean
    private AssessmentService coreAssessmentService;

    @MockitoBean
    private AgreementsService agreementsService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MockMvc mockMvc;

    private SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor validJwtReqPostProcessor;

    @BeforeEach
    void beforeEach() {
        validJwtReqPostProcessor = jwt().authorities(new SimpleGrantedAuthority("CAT_USER"))
                .jwt(jwt -> jwt.subject(PRINCIPAL));
    }

    @Test
    void getMiDetailsReturns200_WhenRecordExists() throws Exception {
        when(miService.getMiDetails(anyString())).thenReturn(givenValidGCloudEProcurement());

        mockMvc.perform(get(MI_PATH)
                        .with(validJwtReqPostProcessor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].questionId").value(212))
                .andExpect(jsonPath("$[0].questionAnswer").value("Summary of work"))
                .andExpect(jsonPath("$[0].createdBy").value("zahid.anwar@crowncommercial.gov.uk"));
    }

    @Test
    void getMiDetails_VerifyAllFieldsMapped() throws Exception {

        var responseItem = MiQuestionAnswer.builder()
                .assessmentId(500)
                .projectId("PRJ-123")
                .eventId("EV-999")
                .questionId(1)
                .questionAnswer("Formatted Answer")
                .createdBy(PRINCIPAL)
                .createdAt(OffsetDateTime.now())
                .build();

        when(miService.getMiDetails(anyString())).thenReturn(List.of(responseItem));

        mockMvc.perform(get(MI_PATH).with(validJwtReqPostProcessor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].assessmentId").value(500))
                .andExpect(jsonPath("$[0].projectId").value("PRJ-123"))
                .andExpect(jsonPath("$[0].eventId").value("EV-999"))
                .andExpect(jsonPath("$[0].questionAnswer").value("Formatted Answer"));
    }

    @Test
    void addMiDetailsReturns200_WhenDataIsValid() throws Exception {
        when(miService.createMiQuestionAndAnswer(any(), anyString())).thenReturn(1);

        // Get the first object from the list to send as a single JSON object
        List<MiQuestionAnswer> payload = givenValidGCloudEProcurement();

        mockMvc.perform(post(MI_PATH)
                        .with(validJwtReqPostProcessor)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(content().string("1"));
    }

    @Test
    void addMiDetailsReturns400_WhenJsonIsMalformed() throws Exception {
        String malformedJson = "{ \"assessmentId\": \"NOT_A_NUMBER\" }";

        mockMvc.perform(post(MI_PATH)
                        .with(validJwtReqPostProcessor)
                        .contentType(APPLICATION_JSON)
                        .content(malformedJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addMiDetails_ReturnsZero_WhenServiceFailsSilently() throws Exception {
        // Simulating the catch block in your controller
        when(miService.createMiQuestionAndAnswer(any(), anyString()))
                .thenThrow(new RuntimeException("DB Connection Failed"));

        List<MiQuestionAnswer> payload = List.of(MiQuestionAnswer.builder().questionId(1).build());

        mockMvc.perform(post(MI_PATH)
                        .with(validJwtReqPostProcessor)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(content().string("0"));
    }

    private List<MiQuestionAnswer> givenValidGCloudEProcurement() {
        return List.of(MiQuestionAnswer.builder()
                .assessmentId(1)
                .projectId("12334")
                .eventId("test-166666")
                .questionId(212)
                .questionAnswer("Summary of work")
                .createdBy("zahid.anwar@crowncommercial.gov.uk")
                .build());
    }

}