package uk.gov.crowncommercial.dts.scale.cat.controller;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;
import uk.gov.crowncommercial.dts.scale.cat.config.ApplicationFlagsConfig;
import uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.*;
import uk.gov.crowncommercial.dts.scale.cat.service.ca.AssessmentService;
import uk.gov.crowncommercial.dts.scale.cat.utils.TendersAPIModelUtils;

/**
 * Controller tests.
 */
@WebMvcTest(AssessmentsController.class)
@Import({TendersAPIModelUtils.class, JaggaerAPIConfig.class, ApplicationFlagsConfig.class})
@ActiveProfiles("test")
class AssessmentsControllerTest {

  private static final String ASSESSMENTS_PATH = "/assessments";
  private static final String PRINCIPAL = "jsmith@ccs.org.uk";
  private static final Integer ASSESSMENT_ID = 1;
  private static final Integer DIMENSION_ID = 1;
  private static final String DIMENSION_NAME = "Security Clearance";
  private static final CriteriaSelectionType CRITERIA_TYPE = CriteriaSelectionType.SELECT;
  private static final String EXTERNAL_TOOL_ID = "2";
  private static final Integer TOOL_ID = 2;
  private static final String OPTION_NAME = "Data Analyst";
  private static final String GROUP_NAME = "Data";
  private static final Integer GROUP_LEVEL = 1;
  private static final String CRITERION_ID = "11";
  private static final String CRITERION_NAME_SUPPLIER = "Supplier";
  private static final String REQUIREMENT_SC_NONE = "0: None";
  private static final String REQUIREMENT_SC_CTC = "2: Counter Terrorist Check (CTC)";

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private AssessmentService assessmentService;

  private JwtRequestPostProcessor validJwtReqPostProcessor;

  @BeforeEach
  void beforeEach() {
    validJwtReqPostProcessor = jwt().authorities(new SimpleGrantedAuthority("CAT_USER"))
        .jwt(jwt -> jwt.subject(PRINCIPAL));
  }

  @Test
  void getAssessments_200_OK() throws Exception {

    var assessmentSummary = new AssessmentSummary();
    assessmentSummary.setAssessmentId(ASSESSMENT_ID);
    assessmentSummary.setExternalToolId(EXTERNAL_TOOL_ID);

    when(assessmentService.getAssessmentsForUser(PRINCIPAL)).thenReturn(List.of(assessmentSummary));

    mockMvc.perform(get(ASSESSMENTS_PATH).with(validJwtReqPostProcessor).accept(APPLICATION_JSON))
        .andDo(print()).andExpect(status().isOk())
        .andExpect(content().contentType(APPLICATION_JSON))
        .andExpect(jsonPath("$[0].assessment-id").value(ASSESSMENT_ID))
        .andExpect(jsonPath("$[0].external-tool-id").value(TOOL_ID));
  }

  @Test
  void getDimensions_200_OK() throws Exception {

    var dimension = new DimensionDefinition();
    dimension.setDimensionId(DIMENSION_ID);
    dimension.setName(DIMENSION_NAME);

    var weightingRange = new WeightingRange();
    weightingRange.setMax(100);
    weightingRange.setMin(0);
    dimension.setWeightingRange(weightingRange);

    var group = new DimensionOptionGroups();
    group.setLevel(GROUP_LEVEL);
    group.setName(GROUP_NAME);

    var option = new DimensionOption();
    option.setName(OPTION_NAME);
    option.setGroups(List.of(group));
    dimension.setOptions(List.of(option));
    dimension.setEvaluationCriteria(getTestCriteria());

    when(assessmentService.getDimensions(TOOL_ID)).thenReturn(Arrays.asList(dimension));

    mockMvc
        .perform(get(ASSESSMENTS_PATH + "/tools/{toolID}/dimensions", TOOL_ID)
            .with(validJwtReqPostProcessor).accept(APPLICATION_JSON))
        .andDo(print()).andExpect(status().isOk())
        .andExpect(content().contentType(APPLICATION_JSON))
        .andExpect(jsonPath("$[0].dimension-id").value(DIMENSION_ID))
        .andExpect(jsonPath("$[0].name").value(DIMENSION_NAME))
        .andExpect(jsonPath("$[0].weightingRange.min").value(0))
        .andExpect(jsonPath("$[0].weightingRange.max").value(100))
        .andExpect(jsonPath("$[0].options[0].name").value(OPTION_NAME))
        .andExpect(jsonPath("$[0].options[0].groups[0].name").value(GROUP_NAME))
        .andExpect(jsonPath("$[0].options[0].groups[0].level").value(GROUP_LEVEL))
        .andExpect(jsonPath("$[0].evaluationCriteria[0].criterion-id").value(CRITERION_ID))
        .andExpect(jsonPath("$[0].evaluationCriteria[0].name").value(CRITERION_NAME_SUPPLIER))
        .andExpect(jsonPath("$[0].evaluationCriteria[0].type").value(CRITERIA_TYPE.getValue()))
        .andExpect(jsonPath("$[0].evaluationCriteria[0].options[0]").value(REQUIREMENT_SC_NONE))
        .andExpect(jsonPath("$[0].evaluationCriteria[0].options[1]").value(REQUIREMENT_SC_CTC));
  }

  @Test
  void getAssessment_200_OK() throws Exception {

    var assessment = new Assessment();
    assessment.setAssessmentId(ASSESSMENT_ID);
    assessment.setExternalToolId(EXTERNAL_TOOL_ID);

    var dimensionRequirement = new DimensionRequirement();
    dimensionRequirement.setDimensionId(DIMENSION_ID);
    dimensionRequirement.setName(DIMENSION_NAME);
    dimensionRequirement.setWeighting(50);
    dimensionRequirement.setIncludedCriteria(getTestCriteria());
    assessment.setDimensionRequirements(List.of(dimensionRequirement));

    var criterion = new Criterion();
    criterion.setCriterionId(CRITERION_ID);
    criterion.setName(CRITERION_NAME_SUPPLIER);
    criterion.setValue(REQUIREMENT_SC_CTC);

    var requirement = new Requirement();
    requirement.setName(OPTION_NAME);
    requirement.setRequirementId(1);
    requirement.setWeighting(25);
    requirement.setValues(List.of(criterion));
    dimensionRequirement.setRequirements(List.of(requirement));

    when(assessmentService.getAssessment(ASSESSMENT_ID, PRINCIPAL)).thenReturn(assessment);

    mockMvc
        .perform(get(ASSESSMENTS_PATH + "/{assessmentID}", ASSESSMENT_ID)
            .with(validJwtReqPostProcessor).accept(APPLICATION_JSON))
        .andDo(print()).andExpect(status().isOk())
        .andExpect(content().contentType(APPLICATION_JSON))
        .andExpect(jsonPath("$.assessment-id").value(ASSESSMENT_ID))
        .andExpect(jsonPath("$.external-tool-id").value(TOOL_ID))
        .andExpect(jsonPath("$.dimensionRequirements[0].dimension-id").value(DIMENSION_ID))
        .andExpect(jsonPath("$.dimensionRequirements[0].name").value(DIMENSION_NAME))
        .andExpect(jsonPath("$.dimensionRequirements[0].weighting").value(50))
        .andExpect(jsonPath("$.dimensionRequirements[0].includedCriteria[0].criterion-id")
            .value(CRITERION_ID))
        .andExpect(jsonPath("$.dimensionRequirements[0].includedCriteria[0].name")
            .value(CRITERION_NAME_SUPPLIER))
        .andExpect(jsonPath("$.dimensionRequirements[0].includedCriteria[0].type")
            .value(CRITERIA_TYPE.getValue()))
        .andExpect(jsonPath("$.dimensionRequirements[0].includedCriteria[0].options[0]")
            .value(REQUIREMENT_SC_NONE))
        .andExpect(jsonPath("$.dimensionRequirements[0].requirements[0].name").value(OPTION_NAME))
        .andExpect(jsonPath("$.dimensionRequirements[0].requirements[0].requirement-id").value(1))
        .andExpect(jsonPath("$.dimensionRequirements[0].requirements[0].weighting").value(25))
        .andExpect(jsonPath("$.dimensionRequirements[0].requirements[0].values[0].criterion-id")
            .value(CRITERION_ID))
        .andExpect(jsonPath("$.dimensionRequirements[0].requirements[0].values[0].name")
            .value(CRITERION_NAME_SUPPLIER))
        .andExpect(jsonPath("$.dimensionRequirements[0].requirements[0].values[0].value")
            .value(REQUIREMENT_SC_CTC));
  }

  @Test
  void createAssessment_200_OK() throws Exception {

    var assessment = new Assessment();

    when(assessmentService.createAssessment(assessment, PRINCIPAL)).thenReturn(1);

    mockMvc
        .perform(post(ASSESSMENTS_PATH).with(validJwtReqPostProcessor).contentType(APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(assessment)))
        .andDo(print()).andExpect(status().isOk())
        .andExpect(content().contentType(APPLICATION_JSON)).andExpect(jsonPath("$").value(1));

    verify(assessmentService, times(1)).createAssessment(assessment, PRINCIPAL);
  }

  private List<CriterionDefinition> getTestCriteria() {
    var criterion = new CriterionDefinition();
    criterion.setCriterionId(CRITERION_ID);
    criterion.setName(CRITERION_NAME_SUPPLIER);
    criterion.setType(CRITERIA_TYPE);
    criterion.setOptions(List.of(REQUIREMENT_SC_NONE, REQUIREMENT_SC_CTC));
    return List.of(criterion);
  }
}
