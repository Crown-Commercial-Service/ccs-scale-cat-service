package uk.gov.crowncommercial.dts.scale.cat.service;


import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.web.reactive.function.client.WebClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.DataTemplate;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.Requirement.Option;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.TemplateCriteria;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.*;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.QuestionNonOCDS.QuestionTypeEnum;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;

/**
 * Service layer tests
 */
@SpringBootTest(classes = {CriteriaService.class, JaggaerAPIConfig.class, ObjectMapper.class},
    webEnvironment = WebEnvironment.NONE)
@EnableConfigurationProperties(JaggaerAPIConfig.class)
class CriteriaServiceTest {

  private static final String EVENT_OCID = "ocds-abc123-1";
  private static final Integer PROJECT_ID = 1;
  private static final String CRITERION_ID = "Criterion 1";
  private static final String GROUP_ID = "Group 1";
  private static final String QUESTION_ID = "Question 1";

  @MockBean
  private AgreementsService agreementsService;

  @MockBean
  private ValidationService validationService;

  @MockBean
  private RetryableTendersDBDelegate retryableTendersDBDelegate;

  @MockBean
  private WebClient jaggaerWebClient;

  @Autowired
  private CriteriaService criteriaService;

  @Autowired
  private ObjectMapper objectMapper;

  @Test
  void testPutQuestionOptionDetails_buyer_multiSelect() throws Exception {

    // Load the existing Data Template - mimics what is in DB - only 'England' selected
    ClassLoader classloader = Thread.currentThread().getContextClassLoader();
    InputStream is = classloader
        .getResourceAsStream("criteria-service-test-data/criteria-buyer-multiselect.json");
    TemplateCriteria criteria = objectMapper.readValue(is, TemplateCriteria.class);
    ProcurementEvent event = new ProcurementEvent();
    event.setProcurementTemplatePayload(
        DataTemplate.builder().criteria(Arrays.asList(criteria)).build());

    // Simulates answering the same question again - only 'Wales' selected
    Requirement1 questionOCDS = new Requirement1();
    questionOCDS.setDataType(DataType.STRING);
    questionOCDS.setTitle("Where will the work be done");
    QuestionNonOCDSOptions option = new QuestionNonOCDSOptions();
    option.setValue("Wales");
    option.setSelected(true);
    QuestionNonOCDS questionNonOCDS = new QuestionNonOCDS();
    questionNonOCDS.setQuestionType(QuestionTypeEnum.MULTISELECT);
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
}
