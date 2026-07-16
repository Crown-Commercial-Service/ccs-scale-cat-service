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
                          "OCDS": { "id": "Question 1", "title": "Enter your question" },
                          "nonOCDS": { "options": [{ "value": "Valid Question Text", "select": true }] }
                        }] 
                      }, 
                      "nonOCDS": { "order": 1 } 
                    },
                    { 
                      "OCDS": { 
                        "id": "Group 2", 
                        "requirements": [{
                          "OCDS": { "id": "Question 1", "title": "Enter your question" },
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
                          "OCDS": { "id": "Question 1", "title": "Enter your question" },
                          "nonOCDS": { "options": [{ "value": "Valid Question Text 2", "select": true }] }
                        }] 
                      }, 
                      "nonOCDS": { "order": 1 } 
                    },
                    { 
                      "OCDS": { 
                        "id": "Group 2", 
                        "requirements": [{
                          "OCDS": { "id": "Question 1", "title": "Enter your question" },
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
                  "OCDS": { "id": "Group 1", "requirements": [{ "OCDS": { "id": "Question 1" }, "nonOCDS": { "options": [{ "value": "Base Q", "select": true }] } }] },
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

        assertThat(requirementGroups.size()).isEqualTo(2);

        assertThat(requirementGroups.get(0).at("/OCDS/id").asText()).isEqualTo("Group 1.1");
        assertThat(requirementGroups.get(1).at("/OCDS/id").asText()).isEqualTo("Group 1.2");

        assertThat(requirementGroups.get(0).at("/nonOCDS/order").asInt()).isEqualTo(1);
        assertThat(requirementGroups.get(1).at("/nonOCDS/order").asInt()).isEqualTo(2);

        JsonNode stage2Reqs = requirementGroups.get(1).at("/OCDS/requirements");

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
    void mergeStageJsonPayloadsSkipsGroupZeroEntirely() throws Exception {

        // "Group 0" is hardcoded to be skipped in the logic
        String payload = """
            {
              "criteria": [{
                "id": "Criterion 2",
                "requirementGroups": [{ 
                  "OCDS": { "id": "Group 0", "requirements": [{ 
                    "OCDS": { "id": "Question 1", "title": "Enter your question" }, "nonOCDS": { "options": [{ "value": "Valid", "select": true }] } 
                  }] }
                }]
              }]
            }
            """;
        List<Map<String, Object>> stageDataList = List.of(createStageMap(payload, 1, "Stage 1", 1));

        String result = docGenService.mergeStageJsonPayloads(stageDataList);

        JsonNode root = objectMapper.readTree(result);
        JsonNode requirementGroups = root.at("/criteria/0/requirementGroups");

        // Group 0 should be actively ignored
        assertThat(requirementGroups.isEmpty()).isTrue();
    }

    @Test
    void mergeStageJsonPayloads_SuccessfullyGroupsByDropdownNameAndInjectsVirtualTagsOnce() throws Exception {

        // Arrange: A JSON payload with two fragmented groups
        String complexPayload = """
            {
              "criteria": [{
                "id": "Criterion 2",
                "requirementGroups": [
                  { 
                    "OCDS": { "id": "Group 2.1", "requirements": [
                        { "OCDS": { "id": "Question 1", "title": "Enter your question" }, "nonOCDS": { "options": [{"value": "Q1 Text"}] } },
                        { "OCDS": { "id": "Question 2", "title": "Select group name" }, "nonOCDS": { "options": [{"value": "Business Requirement"}] } }
                    ]}
                  },
                  { 
                    "OCDS": { "id": "Group 2.2", "requirements": [
                        { "OCDS": { "id": "Question 1", "title": "Enter your question" }, "nonOCDS": { "options": [{"value": "Q2 Text"}] } },
                        { "OCDS": { "id": "Question 2", "title": "Select group name" }, "nonOCDS": { "options": [{"value": "Pricing"}] } }
                    ]}
                  },
                  { 
                    "OCDS": { "id": "Group 2.3", "requirements": [
                        { "OCDS": { "id": "Question 1", "title": "Enter your question" }, "nonOCDS": { "options": [{"value": "Q3 Text"}] } },
                        { "OCDS": { "id": "Question 2", "title": "Select group name" }, "nonOCDS": { "options": [{"value": "Business Requirement"}] } }
                    ]}
                  }
                ]
              }]
            }
            """;

        List<Map<String, Object>> stageDataList = List.of(createStageMap(complexPayload, 1, "Alpha Phase", 1));

        String resultJson = docGenService.mergeStageJsonPayloads(stageDataList);

        assertNotNull(resultJson);
        JsonNode root = objectMapper.readTree(resultJson);
        JsonNode requirementGroups = root.at("/criteria/0/requirementGroups");

        assertEquals(3, requirementGroups.size(), "Should keep 3 groups but sort them sequentially by name.");

        assertEquals("Group 2.1", requirementGroups.get(0).at("/OCDS/id").asText());
        assertEquals("Group 2.3", requirementGroups.get(1).at("/OCDS/id").asText(), "Group 2.3 should be moved to sit next to Group 2.1");

        assertEquals("Group 2.2", requirementGroups.get(2).at("/OCDS/id").asText());

        // Verify Virtual Tags were safely added to the end of each cloned array
        JsonNode group1Reqs = requirementGroups.get(0).at("/OCDS/requirements");
        assertEquals(5, group1Reqs.size(), "Should have 2 original questions + 3 virtual tags");
        assertEquals("CURRENT_STAGE", group1Reqs.get(2).at("/OCDS/id").asText());
    }

    @Test
    void mergeStageJsonPayloadsAppliesSiblingHeuristicClearsGhostState() throws Exception {

        // Arrange: Simulating the exact frontend bug.
        // The base group (Group 2) has a ghost state of "Business Requirement",
        // but its sibling clones (Group 2.1 and Group 2.2) were cleared to "(no group)" by the user.
        String ghostStatePayload = """
            {
              "criteria": [{
                "id": "Criterion 2",
                "requirementGroups": [
                  { 
                    "OCDS": { "id": "Group 2", "requirements": [
                        { "OCDS": { "id": "Question 1", "title": "Enter your question" }, "nonOCDS": { "options": [{"value": "Base Question"}] } },
                        { "OCDS": { "id": "Question 2", "title": "Select group name" }, "nonOCDS": { "options": [{"value": "Business Requirement"}] } }
                    ]}
                  },
                  { 
                    "OCDS": { "id": "Group 2.1", "requirements": [
                        { "OCDS": { "id": "Question 1", "title": "Enter your question" }, "nonOCDS": { "options": [{"value": "Clone Q1"}] } },
                        { "OCDS": { "id": "Question 2", "title": "Select group name" }, "nonOCDS": { "options": [{"value": "(no group)"}] } }
                    ]}
                  },
                  { 
                    "OCDS": { "id": "Group 2.2", "requirements": [
                        { "OCDS": { "id": "Question 1", "title": "Enter your question" }, "nonOCDS": { "options": [{"value": "Clone Q2"}] } },
                        { "OCDS": { "id": "Question 2", "title": "Select group name" }, "nonOCDS": { "options": [{"value": "(no group)"}] } }
                    ]}
                  }
                ]
              }]
            }
            """;

        // Must be Stage 2 or higher for the sibling heuristic to activate
        List<Map<String, Object>> stageDataList = List.of(createStageMap(ghostStatePayload, 2, "Beta Phase", 2));

        String resultJson = docGenService.mergeStageJsonPayloads(stageDataList);

        JsonNode root = objectMapper.readTree(resultJson);
        JsonNode requirementGroups = root.at("/criteria/0/requirementGroups");

        assertEquals(3, requirementGroups.size(), "Should retain 3 distinct groups");

        // Verify the Heuristic successfully scanned the clones and wiped the ghost state from the parent!
        for (JsonNode group : requirementGroups) {
            JsonNode mergedReqs = group.at("/OCDS/requirements");
            for (JsonNode req : mergedReqs) {
                if ("Select group name".equals(req.at("/OCDS/title").asText())) {
                    String sanitizedValue = req.at("/nonOCDS/options/0/value").asText();
                    assertEquals("(no group)", sanitizedValue, "The ghost state group name MUST be overwritten with (no group) on all groups");
                }
            }
        }
    }

    @Test
    void mergeStageJsonPayloadsInterceptsBlankMapsToNoGroup() throws Exception {

        String blankDropdownPayload = """
            {
              "criteria": [{
                "id": "Criterion 2",
                "requirementGroups": [
                  { 
                    "OCDS": { "id": "Group 2.1", "requirements": [
                        { "OCDS": { "id": "Question 1", "title": "Enter your question" }, "nonOCDS": { "options": [{"value": "Valid Q1"}] } },
                        { "OCDS": { "id": "Question 2", "title": "Select group name" }, "nonOCDS": { "options": [{"value": ""}] } }
                    ]}
                  },
                  { 
                    "OCDS": { "id": "Group 2.2", "requirements": [
                        { "OCDS": { "id": "Question 1", "title": "Enter your question" }, "nonOCDS": { "options": [{"value": "Valid Q2"}] } },
                        { "OCDS": { "id": "Question 2", "title": "Select group name" }, "nonOCDS": { "options": [{"value": "null"}] } }
                    ]}
                  }
                ]
              }]
            }
            """;

        List<Map<String, Object>> stageDataList = List.of(createStageMap(blankDropdownPayload, 1, "Alpha Phase", 1));

        String resultJson = docGenService.mergeStageJsonPayloads(stageDataList);

        JsonNode root = objectMapper.readTree(resultJson);
        JsonNode requirementGroups = root.at("/criteria/0/requirementGroups");

        assertEquals(2, requirementGroups.size(), "Should retain 2 distinct groups");

        for (JsonNode group : requirementGroups) {
            JsonNode mergedReqs = group.at("/OCDS/requirements");
            for (JsonNode req : mergedReqs) {
                if ("Select group name".equals(req.at("/OCDS/title").asText())) {
                    String sanitizedValue = req.at("/nonOCDS/options/0/value").asText();
                    assertEquals("(no group)", sanitizedValue, "Blank and null values must be explicitly mapped to '(no group)'");
                }
            }
        }
    }

    private Map<String, Object> createStageMap(String payload, int num, String desc, int total) {
        Map<String, Object> map = new HashMap<>();
        map.put("payload", payload);
        map.put("stageNumber", num);
        map.put("stageDescription", desc);
        map.put("totalStages", total);
        return map;
    }
}