package uk.gov.crowncommercial.dts.scale.cat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(MockitoExtension.class)
class DocGenServiceTest {

    @InjectMocks
    private DocGenService docGenService;

    private final ObjectMapper objectMapper = new ObjectMapper();



    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(docGenService, "objectMapper", new ObjectMapper());
    }

    @Test
    void testMergeStageJsonPayloads() throws Exception {

        String stage1Payload = """
            {
              "criteria": [{
                "id": "Criterion 2",
                "requirementGroups": [
                    { 
                      "OCDS": { 
                        "id": "Group 1", 
                        "requirements": [{
                          "nonOCDS": { "options": [{ "value": "Valid Question Text", "select": true }] }
                        }] 
                      }, 
                      "nonOCDS": { "order": 1 } 
                    },
                    { 
                      "OCDS": { 
                        "id": "Group 2", 
                        "requirements": [{
                          "nonOCDS": { "options": [{ "value": "Valid Award Question Text", "select": true }] }
                        }] 
                      }, 
                      "nonOCDS": { "order": 2 } 
                    }
                ]
              }]
            }
            """;

        String stage2Payload = """
            {
              "criteria": [{
                "id": "Criterion 2",
                "requirementGroups": [
                    { 
                      "OCDS": { 
                        "id": "Group 1", 
                        "requirements": [{
                          "nonOCDS": { "options": [{ "value": "Valid Question Text 2", "select": true }] }
                        }] 
                      }, 
                      "nonOCDS": { "order": 1 } 
                    },
                    { 
                      "OCDS": { 
                        "id": "Group 2", 
                        "requirements": [{
                          "nonOCDS": { "options": [{ "value": "Valid Award Question Text 2", "select": true }] }
                        }] 
                      }, 
                      "nonOCDS": { "order": 2 } 
                    }
                ]
              }]
            }
            """;

        Map<String, Object> stage1 = new HashMap<>();
        stage1.put("payload", stage1Payload);
        stage1.put("stageNumber", 1);
        stage1.put("totalStages", 2);
        stage1.put("stageDescription", "Description for Stage 1");

        Map<String, Object> stage2 = new HashMap<>();
        stage2.put("payload", stage2Payload);
        stage2.put("stageNumber", 2);
        stage2.put("totalStages", 2);
        stage2.put("stageDescription", "Description for Stage 2");

        List<Map<String, Object>> stageDataList = Arrays.asList(stage1, stage2);

        String resultJson = docGenService.mergeStageJsonPayloads(stageDataList);

        assertNotNull(resultJson);
        JsonNode root = objectMapper.readTree(resultJson);

        JsonNode groups = root.at("/criteria/0/requirementGroups");
        assertEquals(4, groups.size(), "Should have exactly 4 requirement groups merged");

        assertEquals("Group 1.1", groups.get(0).at("/OCDS/id").asText());
        assertEquals("Group 1.2", groups.get(2).at("/OCDS/id").asText());

        JsonNode stage2Reqs = groups.get(2).at("/OCDS/requirements");
        assertEquals(4, stage2Reqs.size(), "Should have 4 active items in the requirements node array");

        String stageDesc = "";
        for (JsonNode r : stage2Reqs) {
            String title = r.at("/OCDS/title").asText();
            if ("STAGE_DESCRIPTION".equals(title)) {
                stageDesc = r.at("/nonOCDS/options/0/value").asText();
            }
        }

        assertEquals("Description for Stage 2", stageDesc, "Stage description should match the metadata passed");
        assertEquals(3, groups.get(2).at("/nonOCDS/order").asInt(), "Order should be sequential across stages");
    }

    @Test
    void mergeStageJsonPayloads_WhenInputIsNull_ReturnsEmptyString() {

        String result = docGenService.mergeStageJsonPayloads(null);
        assertThat(result).isEmpty();
    }

    @Test
    void mergeStageJsonPayloads_WhenInputIsEmpty_ReturnsEmptyString() {

        String result = docGenService.mergeStageJsonPayloads(Collections.emptyList());

        assertThat(result).isEmpty();
    }

    @Test
    void mergeStageJsonPayloads_WhenCriterion2IsMissing_ReturnsBaseJsonUnmodified() throws Exception {
        // JSON payload missing "Criterion 2"
        String payload = """
            { "criteria": [ { "id": "Criterion 1", "requirementGroups": [] } ] }
            """;
        List<Map<String, Object>> stageDataList = List.of(createStageMap(payload, 1, "Stage 1", 1));

        String result = docGenService.mergeStageJsonPayloads(stageDataList);

        JsonNode root = objectMapper.readTree(result);
        assertThat(root.at("/criteria/0/id").asText()).isEqualTo("Criterion 1");
    }

    @Test
    void mergeStageJsonPayloads_WhenQuestionTextIsEmpty_SkipsGroupEntirely() throws Exception {
        // Group 1 has an empty value ("") for its question option
        String payload = """
            {
              "criteria": [{
                "id": "Criterion 2",
                "requirementGroups": [{ 
                  "OCDS": { "id": "Group 1", "requirements": [{ "nonOCDS": { "options": [{ "value": "", "select": true }] } }] },
                  "nonOCDS": { "order": 1 }
                }]
              }]
            }
            """;
        List<Map<String, Object>> stageDataList = List.of(createStageMap(payload, 1, "Stage 1", 1));

        String result = docGenService.mergeStageJsonPayloads(stageDataList);

        JsonNode root = objectMapper.readTree(result);
        JsonNode requirementGroups = root.at("/criteria/0/requirementGroups");

        // Question was empty, the filtering logic should strip it out completely
        assertThat(requirementGroups.isEmpty()).isTrue();
    }

    @Test
    void mergeStageJsonPayloads_WhenGroupIdIsUnrecognized_SkipsGroupEntirely() throws Exception {
        // Group ID is "Group 99", which is not something we are interested, negative tests
        String payload = """
            {
              "criteria": [{
                "id": "Criterion 2",
                "requirementGroups": [{ 
                  "OCDS": { "id": "Group 99", "requirements": [{ "nonOCDS": { "options": [{ "value": "Valid", "select": true }] } }] },
                  "nonOCDS": { "order": 1 }
                }]
              }]
            }
            """;
        List<Map<String, Object>> stageDataList = List.of(createStageMap(payload, 1, "Stage 1", 1));

        String result = docGenService.mergeStageJsonPayloads(stageDataList);

        JsonNode root = objectMapper.readTree(result);
        JsonNode requirementGroups = root.at("/criteria/0/requirementGroups");

        // Unrecognized groups don't get a stageAdjustedId, so they are not added
        assertThat(requirementGroups.isEmpty()).isTrue();
    }

    @Test
    void mergeStageJsonPayloads_ValidMultiStageInput_SuccessfullyMergesAndInjectsVirtualTags() throws Exception {

        String basePayload = """
            {
              "criteria": [{
                "id": "Criterion 2",
                "requirementGroups": [{ 
                  "OCDS": { "id": "Group 1", "requirements": [{ "nonOCDS": { "options": [{ "value": "Base Q", "select": true }] } }] },
                  "nonOCDS": { "order": 1 }
                }]
              }]
            }
            """;

        List<Map<String, Object>> stageDataList = Arrays.asList(
                createStageMap(basePayload, 1, "Discovery Phase", 2),
                createStageMap(basePayload, 2, "Alpha Phase", 2)
        );

        String result = docGenService.mergeStageJsonPayloads(stageDataList);

        JsonNode root = objectMapper.readTree(result);
        JsonNode requirementGroups = root.at("/criteria/0/requirementGroups");

        // We passed 2 stages, each containing 1 valid group. Expecting 2 groups in the final merged array.
        assertThat(requirementGroups.size()).isEqualTo(2);

        assertThat(requirementGroups.get(0).at("/OCDS/id").asText()).isEqualTo("Group 1.1");
        assertThat(requirementGroups.get(1).at("/OCDS/id").asText()).isEqualTo("Group 1.2");

        assertThat(requirementGroups.get(0).at("/nonOCDS/order").asInt()).isEqualTo(1);
        assertThat(requirementGroups.get(1).at("/nonOCDS/order").asInt()).isEqualTo(2);

        JsonNode stage2Reqs = requirementGroups.get(1).at("/OCDS/requirements");

        // Should contain 1 base requirement + 3 injected tags = 4 requirements
        assertThat(stage2Reqs.size()).isEqualTo(4);

        boolean foundCurrentStage = false;
        boolean foundStageDesc = false;

        for (JsonNode req : stage2Reqs) {
            String title = req.at("/OCDS/title").asText();
            String value = req.at("/nonOCDS/options/0/value").asText();

            if ("CURRENT_STAGE".equals(title) && "2".equals(value)) {
                foundCurrentStage = true;
            }
            if ("STAGE_DESCRIPTION".equals(title) && "Alpha Phase".equals(value)) {
                foundStageDesc = true;
            }
        }

        assertThat(foundCurrentStage).as("CURRENT_STAGE virtual tag was injected correctly").isTrue();
        assertThat(foundStageDesc).as("STAGE_DESCRIPTION virtual tag was injected correctly").isTrue();
    }

    @Test
    void testMergeStageWithFourJsonPayloads() throws Exception {
        List<Map<String, Object>> stageDataList = new ArrayList<>();

        stageDataList.add(createStageMap(STAGE_1, 1, "Zahid - Stage description - Stage 5.1", 4));
        stageDataList.add(createStageMap(STAGE_2, 2, "Zahid - Stage description - Stage 5.2", 4));
        stageDataList.add(createStageMap(STAGE_3, 3, "Zahid - Stage description - Stage 5.3", 4));
        stageDataList.add(createStageMap(STAGE_4, 4, "Zahid - Stage description - Stage 5.4", 4));

        String resultJson = docGenService.mergeStageJsonPayloads(stageDataList);
        JsonNode root = objectMapper.readTree(resultJson);

        JsonNode crit2 = null;
        for (JsonNode c : root.get("criteria")) {
            if ("Criterion 2".equals(c.get("id").asText())) {
                crit2 = c;
                break;
            }
        }

        assertNotNull(crit2, "Criterion 2 should exist");
        JsonNode groups = crit2.get("requirementGroups");

        // Stage 1, 2, 3, and 4 each add exactly 2 structural groups = 8 total groups
        assertEquals(8, groups.size(), "Total groups should be 8");

        assertEquals("Group 2.2", groups.get(3).at("/OCDS/id").asText());
        assertEquals("Group 2.4", groups.get(7).at("/OCDS/id").asText());

        // Verify Metadata Injection for Stage 4 (Index 7 - Group 2.4)
        JsonNode group24Reqs = groups.get(7).at("/OCDS/requirements");

        boolean foundMetadata = false;
        for (JsonNode req : group24Reqs) {
            if ("CURRENT_STAGE".equals(req.at("/OCDS/title").asText()) &&
                    "4".equals(req.at("/nonOCDS/options/0/value").asText())) {
                foundMetadata = true;
                break;
            }
        }
        assertEquals(true, foundMetadata, "Metadata check for Stage 4 should match successfully");

        // Verify Stage Description for Stage 4
        String actualDesc = "";
        for (JsonNode req : group24Reqs) {
            if ("STAGE_DESCRIPTION".equals(req.at("/OCDS/title").asText())) {
                actualDesc = req.at("/nonOCDS/options/0/value").asText();
            }
        }
        assertEquals("Zahid - Stage description - Stage 5.4", actualDesc);

        JsonNode expectedRoot = objectMapper.readTree(EXPECTED_RESULT_JSON);
        assertEquals(expectedRoot, root, "Result JSON object graph hierarchy should fully match the expected layout signature definition");
    }

    private Map<String, Object> createStageMap(String payload, int num, String desc, int total) {
        Map<String, Object> map = new HashMap<>();
        map.put("payload", payload);
        map.put("stageNumber", num);
        map.put("stageDescription", desc);
        map.put("totalStages", total);
        return map;
    }


    final String STAGE_1 = """
            {"id":35,"criteria":[{"id":"Criterion 1","title":"About the procurement competition","requirementGroups":[{"OCDS":{"id":"Key Dates","description":"Timeline","requirements":[]},"nonOCDS":{"task":"Add timeline","order":1,"mandatory":false}}]},{"id":"Criterion 2","title":"How to bid including evaluation criteria.","requirementGroups":[{"OCDS":{"id":"Group 0","description":"Set overall award criteria weightings","requirements":[{"OCDS":{"id":"Question 1","title":"Quality"},"nonOCDS":{"options":[{"value":"90","select":true}]}}]},"nonOCDS":{"order":0}},{"OCDS":{"id":"Group 1","description":"Conditions of participation","requirements":[{"OCDS":{"id":"Question 1","title":"Enter your question"},"nonOCDS":{"options":[{"value":"Enter any questions you would like to ask for your conditions for participation.","select":true}]}}]},"nonOCDS":{"order":1}},{"OCDS":{"id":"Group 2","description":"Award criteria","requirements":[{"OCDS":{"id":"Question 1","title":"Enter your question"},"nonOCDS":{"options":[{"value":"For example:a question, scenario or presentation brief.","select":true}]}}]},"nonOCDS":{"order":2}}]}]}
            """;

    final String STAGE_2 = """
            {"id":35,"criteria":[{"id":"Criterion 1","title":"About the procurement competition","requirementGroups":[{"OCDS":{"id":"Key Dates","description":"Timeline","requirements":[]},"nonOCDS":{"task":"Add timeline","order":1,"mandatory":false}}]},{"id":"Criterion 2","title":"How to bid including evaluation criteria.","requirementGroups":[{"OCDS":{"id":"Group 0","description":"Set overall award criteria weightings","requirements":[{"OCDS":{"id":"Question 1","title":"Quality"},"nonOCDS":{"options":[{"value":"90","select":true}]}}]},"nonOCDS":{"order":0}},{"OCDS":{"id":"Group 1","description":"Conditions of participation","requirements":[{"OCDS":{"id":"Question 1","title":"Enter your question"},"nonOCDS":{"options":[{"value":"Enter any questions you would like to ask for your conditions for participation.","select":true}]}}]},"nonOCDS":{"order":1}},{"OCDS":{"id":"Group 2","description":"Award criteria","requirements":[{"OCDS":{"id":"Question 1","title":"Enter your question"},"nonOCDS":{"options":[{"value":"For example:a question, scenario or presentation brief.","select":true}]}}]},"nonOCDS":{"order":2}}]}]}
            """;

    final String STAGE_3 = """
            {"id":35,"criteria":[{"id":"Criterion 1","title":"About the procurement competition","requirementGroups":[{"OCDS":{"id":"Key Dates","description":"Timeline","requirements":[]},"nonOCDS":{"task":"Add timeline","order":1,"mandatory":false}}]},{"id":"Criterion 2","title":"How to bid including evaluation criteria.","requirementGroups":[{"OCDS":{"id":"Group 0","description":"Set overall award criteria weightings","requirements":[{"OCDS":{"id":"Question 1","title":"Quality"},"nonOCDS":{"options":[{"value":"90","select":true}]}}]},"nonOCDS":{"order":0}},{"OCDS":{"id":"Group 1","description":"Conditions of participation","requirements":[{"OCDS":{"id":"Question 1","title":"Enter your question"},"nonOCDS":{"options":[{"value":"Enter any questions you would like to ask for your conditions for participation.","select":true}]}}]},"nonOCDS":{"order":1}},{"OCDS":{"id":"Group 2","description":"Award criteria","requirements":[{"OCDS":{"id":"Question 1","title":"Enter your question"},"nonOCDS":{"options":[{"value":"For example:a question, scenario or presentation brief.","select":true}]}}]},"nonOCDS":{"order":2}}]}]}
            """;

    final String STAGE_4 = """
            {"id":35,"criteria":[{"id":"Criterion 1","title":"About the procurement competition","requirementGroups":[{"OCDS":{"id":"Key Dates","description":"Timeline","requirements":[]},"nonOCDS":{"task":"Add timeline","order":1,"mandatory":false}}]},{"id":"Criterion 2","title":"How to bid including evaluation criteria.","requirementGroups":[{"OCDS":{"id":"Group 0","description":"Set overall award criteria weightings","requirements":[{"OCDS":{"id":"Question 1","title":"Quality"},"nonOCDS":{"options":[{"value":"90","select":true}]}}]},"nonOCDS":{"order":0}},{"OCDS":{"id":"Group 1","description":"Conditions of participation","requirements":[{"OCDS":{"id":"Question 1","title":"Enter your question"},"nonOCDS":{"options":[{"value":"Enter any questions you would like to ask for your conditions for participation.","select":true}]}}]},"nonOCDS":{"order":1}},{"OCDS":{"id":"Group 2","description":"Award criteria","requirements":[{"OCDS":{"id":"Question 1","title":"Enter your question"},"nonOCDS":{"options":[{"value":"For example:a question, scenario or presentation brief.","select":true}]}}]},"nonOCDS":{"order":2}}]}]}
            """;

    final String EXPECTED_RESULT_JSON = """
            {"id":35,"criteria":[{"id":"Criterion 1","title":"About the procurement competition","requirementGroups":[{"OCDS":{"id":"Key Dates","description":"Timeline","requirements":[]},"nonOCDS":{"task":"Add timeline","order":1,"mandatory":false}}]},{"id":"Criterion 2","title":"How to bid including evaluation criteria.","requirementGroups":[{"OCDS":{"id":"Group 1.1","description":"Conditions of participation","requirements":[{"OCDS":{"id":"Question 1","title":"Enter your question"},"nonOCDS":{"options":[{"value":"Enter any questions you would like to ask for your conditions for participation.","select":true}]}},{"OCDS":{"id":"CURRENT_STAGE","title":"CURRENT_STAGE"},"nonOCDS":{"options":[{"value":"1","select":true}]}},{"OCDS":{"id":"TOTAL_STAGES","title":"TOTAL_STAGES"},"nonOCDS":{"options":[{"value":"4","select":true}]}},{"OCDS":{"id":"STAGE_DESCRIPTION","title":"STAGE_DESCRIPTION"},"nonOCDS":{"options":[{"value":"Zahid - Stage description - Stage 5.1","select":true}]}}]},"nonOCDS":{"order":1}},{"OCDS":{"id":"Group 2.1","description":"Award criteria","requirements":[{"OCDS":{"id":"Question 1","title":"Enter your question"},"nonOCDS":{"options":[{"value":"For example:a question, scenario or presentation brief.","select":true}]}},{"OCDS":{"id":"CURRENT_STAGE","title":"CURRENT_STAGE"},"nonOCDS":{"options":[{"value":"1","select":true}]}},{"OCDS":{"id":"TOTAL_STAGES","title":"TOTAL_STAGES"},"nonOCDS":{"options":[{"value":"4","select":true}]}},{"OCDS":{"id":"STAGE_DESCRIPTION","title":"STAGE_DESCRIPTION"},"nonOCDS":{"options":[{"value":"Zahid - Stage description - Stage 5.1","select":true}]}}]},"nonOCDS":{"order":2}},{"OCDS":{"id":"Group 1.2","description":"Conditions of participation","requirements":[{"OCDS":{"id":"Question 1","title":"Enter your question"},"nonOCDS":{"options":[{"value":"Enter any questions you would like to ask for your conditions for participation.","select":true}]}},{"OCDS":{"id":"CURRENT_STAGE","title":"CURRENT_STAGE"},"nonOCDS":{"options":[{"value":"2","select":true}]}},{"OCDS":{"id":"TOTAL_STAGES","title":"TOTAL_STAGES"},"nonOCDS":{"options":[{"value":"4","select":true}]}},{"OCDS":{"id":"STAGE_DESCRIPTION","title":"STAGE_DESCRIPTION"},"nonOCDS":{"options":[{"value":"Zahid - Stage description - Stage 5.2","select":true}]}}]},"nonOCDS":{"order":3}},{"OCDS":{"id":"Group 2.2","description":"Award criteria","requirements":[{"OCDS":{"id":"Question 1","title":"Enter your question"},"nonOCDS":{"options":[{"value":"For example:a question, scenario or presentation brief.","select":true}]}},{"OCDS":{"id":"CURRENT_STAGE","title":"CURRENT_STAGE"},"nonOCDS":{"options":[{"value":"2","select":true}]}},{"OCDS":{"id":"TOTAL_STAGES","title":"TOTAL_STAGES"},"nonOCDS":{"options":[{"value":"4","select":true}]}},{"OCDS":{"id":"STAGE_DESCRIPTION","title":"STAGE_DESCRIPTION"},"nonOCDS":{"options":[{"value":"Zahid - Stage description - Stage 5.2","select":true}]}}]},"nonOCDS":{"order":4}},{"OCDS":{"id":"Group 1.3","description":"Conditions of participation","requirements":[{"OCDS":{"id":"Question 1","title":"Enter your question"},"nonOCDS":{"options":[{"value":"Enter any questions you would like to ask for your conditions for participation.","select":true}]}},{"OCDS":{"id":"CURRENT_STAGE","title":"CURRENT_STAGE"},"nonOCDS":{"options":[{"value":"3","select":true}]}},{"OCDS":{"id":"TOTAL_STAGES","title":"TOTAL_STAGES"},"nonOCDS":{"options":[{"value":"4","select":true}]}},{"OCDS":{"id":"STAGE_DESCRIPTION","title":"STAGE_DESCRIPTION"},"nonOCDS":{"options":[{"value":"Zahid - Stage description - Stage 5.3","select":true}]}}]},"nonOCDS":{"order":5}},{"OCDS":{"id":"Group 2.3","description":"Award criteria","requirements":[{"OCDS":{"id":"Question 1","title":"Enter your question"},"nonOCDS":{"options":[{"value":"For example:a question, scenario or presentation brief.","select":true}]}},{"OCDS":{"id":"CURRENT_STAGE","title":"CURRENT_STAGE"},"nonOCDS":{"options":[{"value":"3","select":true}]}},{"OCDS":{"id":"TOTAL_STAGES","title":"TOTAL_STAGES"},"nonOCDS":{"options":[{"value":"4","select":true}]}},{"OCDS":{"id":"STAGE_DESCRIPTION","title":"STAGE_DESCRIPTION"},"nonOCDS":{"options":[{"value":"Zahid - Stage description - Stage 5.3","select":true}]}}]},"nonOCDS":{"order":6}},{"OCDS":{"id":"Group 1.4","description":"Conditions of participation","requirements":[{"OCDS":{"id":"Question 1","title":"Enter your question"},"nonOCDS":{"options":[{"value":"Enter any questions you would like to ask for your conditions for participation.","select":true}]}},{"OCDS":{"id":"CURRENT_STAGE","title":"CURRENT_STAGE"},"nonOCDS":{"options":[{"value":"4","select":true}]}},{"OCDS":{"id":"TOTAL_STAGES","title":"TOTAL_STAGES"},"nonOCDS":{"options":[{"value":"4","select":true}]}},{"OCDS":{"id":"STAGE_DESCRIPTION","title":"STAGE_DESCRIPTION"},"nonOCDS":{"options":[{"value":"Zahid - Stage description - Stage 5.4","select":true}]}}]},"nonOCDS":{"order":7}},{"OCDS":{"id":"Group 2.4","description":"Award criteria","requirements":[{"OCDS":{"id":"Question 1","title":"Enter your question"},"nonOCDS":{"options":[{"value":"For example:a question, scenario or presentation brief.","select":true}]}},{"OCDS":{"id":"CURRENT_STAGE","title":"CURRENT_STAGE"},"nonOCDS":{"options":[{"value":"4","select":true}]}},{"OCDS":{"id":"TOTAL_STAGES","title":"TOTAL_STAGES"},"nonOCDS":{"options":[{"value":"4","select":true}]}},{"OCDS":{"id":"STAGE_DESCRIPTION","title":"STAGE_DESCRIPTION"},"nonOCDS":{"options":[{"value":"Zahid - Stage description - Stage 5.4","select":true}]}}]},"nonOCDS":{"order":8}}]}]}
            """;
}