package uk.gov.crowncommercial.dts.scale.cat.processors.inheritance;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.util.ResourceUtils;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.DataTemplate;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.Requirement;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.RequirementGroup;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.TemplateCriteria;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.DataTemplateInheritanceType;
import uk.gov.crowncommercial.dts.scale.cat.processors.DataTemplateProcessor;

import java.io.File;
import java.io.IOException;
import java.util.List;

@SpringBootTest(classes = {TemplateProcessor.class},
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Slf4j
public class DataTemplateTest {
    private ObjectMapper objectMapper = new ObjectMapper();

    private final String TEST_DATA_FOLDER = "classpath:templates/question_templates/prev_data/";
    private final String TEMPLATE_FOLDER = "classpath:templates/question_templates/input_template/";

    private final String EXPECTED_RESULT_FOLDER = "classpath:templates/question_templates/expected_result/";

    @BeforeEach
    public void init() {
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Autowired
    private DataTemplateProcessor processor;


    @Test
    public void TestDependency() throws IOException {
        String filename = "part_dependency02.json";
        String dataFile = "data.json";
        verify(filename, dataFile);
    }

    @Test
    public void TestNoDepdencyTemplate() throws IOException {
        String filename = "no_dependency.json";
        String dataFile = "data.json";
        verify(filename, dataFile);
    }

    @Test
    public void TestNoneDepdencyTemplate() throws IOException {
        String filename = "none_dependency.json";
        String dataFile = "data.json";
        verify(filename, dataFile);
    }

    private void verify(String fileName, String dataFileName) throws IOException {
        File templateFile = ResourceUtils.getFile(TEMPLATE_FOLDER + fileName);
        DataTemplate template = objectMapper.readerFor(DataTemplate.class).readValue(templateFile);

        File dataFile = ResourceUtils.getFile(TEST_DATA_FOLDER + dataFileName);
        DataTemplate prevData = objectMapper.readerFor(DataTemplate.class).readValue(dataFile);

        File expectedDataFile = ResourceUtils.getFile(EXPECTED_RESULT_FOLDER + fileName);
        DataTemplate expectedData = objectMapper.readerFor(DataTemplate.class).readValue(expectedDataFile);

        processor.process(template, prevData);

        assertEquals(template, expectedData);
    }

    private void assertEquals(DataTemplate dataTemplate, DataTemplate expectedData) {
        List<TemplateCriteria> templates = dataTemplate.getCriteria();
        for (TemplateCriteria template : templates) {
            TemplateCriteria expectedTemplate = expectedData.getCriteria().stream().filter(ct -> ct.getId().equals(template.getId())).findFirst().orElse(null);
            if (null != expectedTemplate) {
                if (!inheritanceEquals(template.getInheritanceNonOCDS(), expectedTemplate.getInheritanceNonOCDS())) {
                    Assertions.fail("inheritanceNonOCDS mismatch - actual:" + template.getInheritanceNonOCDS() + ", expected:" + expectedTemplate.getInheritanceNonOCDS());
                    return;
                }

                for (RequirementGroup reqGroup : template.getRequirementGroups()) {
                    RequirementGroup expectedReqGroup = expectedTemplate.getRequirementGroups().stream().filter(rg -> rg.getOcds().getId().equals(reqGroup.getOcds().getId())).findFirst().orElse(null);
                    if (null != expectedReqGroup) {
                        if (!inheritanceEquals(reqGroup.getNonOCDS().getInheritance(), expectedReqGroup.getNonOCDS().getInheritance())) {
                            Assertions.fail("inheritance mismatch for requirementGroup '" + reqGroup.getOcds().getId() +":" + reqGroup.getOcds().getDescription() + "'" +
                                    " - actual:" + reqGroup.getNonOCDS().getInheritance() + ", expected:" + expectedReqGroup.getNonOCDS().getInheritance());
                            return;
                        }

                        for(Requirement req : reqGroup.getOcds().getRequirements()){
                            Requirement expectedRequirement = expectedReqGroup.getOcds().getRequirements().stream().filter(rq -> rq.getOcds().getId().equals(req.getOcds().getId())).findFirst().orElse(null);
                            if(null != expectedRequirement){
                                if (!inheritanceEquals(req.getNonOCDS().getInheritance(), expectedRequirement.getNonOCDS().getInheritance())){
                                    Assertions.fail("inheritance mismatch for group/requirement '" + reqGroup.getOcds().getId() +"/"
                                            + req.getOcds().getId() + ":" + req.getOcds().getDescription() + "'" +
                                            " - actual:" + req.getNonOCDS().getInheritance() + ", expected:" + expectedRequirement.getNonOCDS().getInheritance());
                                    return;
                                }
                            }else{
                                Assertions.fail("Requirement not found  " + req.getOcds().getDescription());
                                return;
                            }
                        }

                    }else{
                        Assertions.fail("Requirement Group not found  " + reqGroup.getOcds().getDescription());
                        return;
                    }
                }

            } else {
                Assertions.fail("Criteria not found  " + template.getTitle() + " " + template.getDescription());
                return;
            }
        }
    }


    private boolean inheritanceEquals(DataTemplateInheritanceType actual, DataTemplateInheritanceType expected) {
        if (null != actual) {
            if (null != expected)
                return actual.getValue().equals(expected.getValue());
            return false;
        } else {
            return null == expected;
        }
    }
}
