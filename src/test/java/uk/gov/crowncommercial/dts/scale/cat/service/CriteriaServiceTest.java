package uk.gov.crowncommercial.dts.scale.cat.service;


import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.web.reactive.function.client.WebClient;
import uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.mapper.DependencyMapper;
import uk.gov.crowncommercial.dts.scale.cat.mapper.TimelineDependencyMapper;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.Requirement;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.*;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.RequirementGroup;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.Requirement.Option;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementProject;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.QuestionType;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.*;
import uk.gov.crowncommercial.dts.scale.cat.processors.DataTemplateProcessor;
import uk.gov.crowncommercial.dts.scale.cat.processors.ProcurementEventHelperService;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Service layer tests
 */
@SpringBootTest(classes = {CriteriaService.class, JaggaerAPIConfig.class, ObjectMapper.class},
    webEnvironment = WebEnvironment.NONE)
class CriteriaServiceTest {

  private static final String EVENT_OCID = "ocds-abc123-1";
  private static final Integer PROJECT_ID = 1;
  private static final String CRITERION_ID = "Criterion 1";
  private static final String CRITERION_TITLE = "Test Criterion";
  private static final String GROUP_ID = "Group 1";
  private static final String QUESTION_ID = "Question 1";
  private static final String AGREEMENT_NO = "TEST";

  @MockBean
  private AgreementsService agreementsService;

  @MockBean
  private ValidationService validationService;

  @MockBean
  private JaggaerQueueService jaggaerQueueService;

  @MockBean
  private RetryableTendersDBDelegate retryableTendersDBDelegate;

  @MockBean
  private WebClient jaggaerWebClient;

  @MockBean
  private DependencyMapper dependencyMapper;

  @MockBean
  private TimelineDependencyMapper timelineDependencyMapper;

  @Autowired
  private CriteriaService criteriaService;

  @Autowired
  private ObjectMapper objectMapper;

  @MockBean
  private DataTemplateProcessor templateProcessor;

  @MockBean
  private ProcurementEventHelperService eventHelperService;

  @Test
  void testPutQuestionOptionDetails_buyer_multiSelect() throws Exception {

    var procurementProject = ProcurementProject.builder().caNumber(AGREEMENT_NO).build();

    ProcurementEvent event = new ProcurementEvent();
    event.setProject(procurementProject);
    event.setProcurementTemplatePayload(
        getDataTemplate("criteria-service-test-data/criteria-buyer-multiselect.json"));

    // Simulates answering the same question again - only 'Wales' selected
    Requirement1 questionOCDS = new Requirement1();
    questionOCDS.setDataType(DataType.STRING);
    questionOCDS.setTitle("Where will the work be done");
    QuestionNonOCDSOptions option = new QuestionNonOCDSOptions();
    option.setValue("Wales");
    option.setSelected(true);
    QuestionNonOCDS questionNonOCDS = new QuestionNonOCDS();
    questionNonOCDS.setQuestionType(QuestionType.MULTISELECT);
    questionNonOCDS.setMultiAnswer(true);
    questionNonOCDS.setOptions(Arrays.asList(option));
    Question question = new Question();
    question.setOCDS(questionOCDS);
    question.setNonOCDS(questionNonOCDS);

    when(validationService.validateProjectAndEventIds(PROJECT_ID, EVENT_OCID)).thenReturn(event);

    criteriaService.putQuestionOptionDetails(question, PROJECT_ID, EVENT_OCID, CRITERION_ID,
        GROUP_ID, QUESTION_ID);

    List<Option> options = event.getProcurementTemplatePayload().getCriteria().stream().findFirst()
        .get().getRequirementGroups().stream().findFirst().get().getOcds().getRequirements()
        .stream().findFirst().get().getNonOCDS().getOptions();

    // Expectation is that 'Wales' is now the only option selected
    assertFalse(
        options.stream().filter(o -> o.getValue().equals("England")).findFirst().get().getSelect(),
        "England should be false");
    assertFalse(
        options.stream().filter(o -> o.getValue().equals("Scotland")).findFirst().get().getSelect(),
        "Scotland should be false");
    assertTrue(
        options.stream().filter(o -> o.getValue().equals("Wales")).findFirst().get().getSelect(),
        "Wales should be true");
  }

  @Test
  void testGetEvalCriteriaWithoutBuyerQuestions() throws Exception {
    ProcurementEvent event = new ProcurementEvent();
    event.setProcurementTemplatePayload(
        getDataTemplate("criteria-service-test-data/criteria-buyer-multiselect.json"));

    when(validationService.validateProjectAndEventIds(PROJECT_ID, EVENT_OCID)).thenReturn(event);

    var response = criteriaService.getEvalCriteria(PROJECT_ID, EVENT_OCID, false);
    var criterion = response.stream().findFirst().get();

    assertEquals(CRITERION_ID, criterion.getId());
    assertEquals(CRITERION_TITLE, criterion.getTitle());
    assertNull(criterion.getRequirementGroups());
  }

  @Test
  void testGetEvalCriteriaWithBuyerQuestions() throws Exception {
    var procurementProject = ProcurementProject.builder().caNumber(AGREEMENT_NO).build();
    ProcurementEvent event = new ProcurementEvent();
    event.setProject(procurementProject);
    event.setProcurementTemplatePayload(
        getDataTemplate("criteria-service-test-data/criteria-buyer-multiselect.json"));

    when(validationService.validateProjectAndEventIds(PROJECT_ID, EVENT_OCID)).thenReturn(event);

    var response = criteriaService.getEvalCriteria(PROJECT_ID, EVENT_OCID, true);
    var criterion = response.stream().findFirst().get();
    assertEquals(CRITERION_ID, criterion.getId());
    assertEquals("Test Criterion", criterion.getTitle());
    assertEquals(1, criterion.getRequirementGroups().size());

    var group = criterion.getRequirementGroups().stream().findFirst().get();
    assertEquals(GROUP_ID, group.getOCDS().getId());

    var question = group.getOCDS().getRequirements().stream().findFirst().get();
    assertEquals(QUESTION_ID, question.getOCDS().getId());
  }

  @Test
  void testValidateMinMaxContractValues() {
    var minRNonocd = Requirement.NonOCDS.builder().questionType("Monetary")
        .options(Arrays.asList(Option.builder().value("107").build()))
        .dependency(Dependency.builder()
            .relationships(
                Arrays.asList(Relationships.builder().dependentOnID("Question 1").build()))
            .build())
        .build();
    var minROcd = Requirement.OCDS.builder().id("Question 2").build();
    var minRe = Requirement.builder().ocds(minROcd).nonOCDS(minRNonocd).build();

    var maxRNonocd = Requirement.NonOCDS.builder().questionType("Monetary")
        .options(Arrays.asList(Option.builder().value("100").build())).build();
    var maxROcd = Requirement.OCDS.builder().id("Question 1").build();
    var maxRe = Requirement.builder().ocds(maxROcd).nonOCDS(maxRNonocd).build();

    var ocds = RequirementGroup.OCDS.builder().id("Group 21")
        .requirements(new HashSet<Requirement>(Arrays.asList(minRe, maxRe))).build();
    RequirementGroup group = RequirementGroup.builder().ocds(ocds).build();

    criteriaService.validateQuestionsValues(group, minRe,
        Arrays.asList(new QuestionNonOCDSOptions().value("107")));
    criteriaService.validateQuestionsValues(group, maxRe,
        Arrays.asList(new QuestionNonOCDSOptions().value("100")));
    // Verify
    verify(validationService, times(2)).validateMinMaxValue(BigDecimal.valueOf(100),
        BigDecimal.valueOf(107));
  }

  private DataTemplate getDataTemplate(final String filePath) throws Exception {
    // Load the existing Data Template - mimics what is in DB - only 'England' selected
    ClassLoader classloader = Thread.currentThread().getContextClassLoader();
    InputStream is = classloader.getResourceAsStream(filePath);
    TemplateCriteria criteria = objectMapper.readValue(is, TemplateCriteria.class);
    return DataTemplate.builder().criteria(Arrays.asList(criteria)).build();
  }

}
