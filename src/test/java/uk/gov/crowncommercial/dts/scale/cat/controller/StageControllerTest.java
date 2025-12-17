package uk.gov.crowncommercial.dts.scale.cat.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import uk.gov.crowncommercial.dts.scale.cat.config.ApplicationFlagsConfig;
import uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.config.OAuth2Config;
import uk.gov.crowncommercial.dts.scale.cat.exception.StageException;
import uk.gov.crowncommercial.dts.scale.cat.model.cas.generated.StageType;
import uk.gov.crowncommercial.dts.scale.cat.model.cas.generated.StageTypesRead;
import uk.gov.crowncommercial.dts.scale.cat.model.cas.generated.Stages;
import uk.gov.crowncommercial.dts.scale.cat.model.cas.generated.StagesRead;
import uk.gov.crowncommercial.dts.scale.cat.model.cas.generated.StagesWrite;
import uk.gov.crowncommercial.dts.scale.cat.service.StageService;
import uk.gov.crowncommercial.dts.scale.cat.utils.TendersAPIModelUtils;

/**
 * Web (mock MVC) Stage Controller tests
 */
@WebMvcTest(StageController.class)
@Import({TendersAPIModelUtils.class, JaggaerAPIConfig.class, ApplicationFlagsConfig.class, OAuth2Config.class})
@ActiveProfiles("test")
public class StageControllerTest {
    private static final String EVENT_ID = "eventId";

    private static final String MODULE_1 = "Module 1 - Initial Stages of a Multi Stage Competitive Selection Process";
    private static final String MODULE_2 = "Module 2 - Conditions of Participation Assessment";
    private static final String MODULE_3 = "Module 3 - Tendering Stage";
    private static final String MODULE_4 = "Module 4 - Presentation/Demonstration Stage";
    private static final String MODULE_5 = "Module 5 - Site Visit Stage";
    private static final String MODULE_6 = "Module 6 - Dialogue Stage";
    private static final String MODULE_7 = "Module 7 - Negotiation Stage";
    private static final String MODULE_8 = "Module 8 - Final Tendering Stage";

    private static SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor validCATJwtReqPostProcessor;

    private static StagesWrite stagesWrite;
    private static String stageRequestJson;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    StageService stageService;

    @BeforeAll
    public static void beforeEach() throws Exception {
        validCATJwtReqPostProcessor = jwt().authorities(new SimpleGrantedAuthority("CAT_USER"));

        stagesWrite = new StagesWrite();
        stagesWrite.addStagesItem(new Stages().id(2));
        stagesWrite.addStagesItem(new Stages().id(4));
        stagesWrite.addStagesItem(new Stages().id(6));

        stageRequestJson = new ObjectMapper().writeValueAsString(stagesWrite);
    }

    @Test
    public void shouldReturnAllStageTypes() throws Exception {
      final var stageType1 = new StageType().id(1).stageType(MODULE_1);
      final var stageType2 = new StageType().id(2).stageType(MODULE_2);
      final var stageType3 = new StageType().id(3).stageType(MODULE_3);
      final var stageType4 = new StageType().id(4).stageType(MODULE_4);
      final var stageType5 = new StageType().id(5).stageType(MODULE_5);
      final var stageType6 = new StageType().id(6).stageType(MODULE_6);
      final var stageType7 = new StageType().id(7).stageType(MODULE_7);
      final var stageType8 = new StageType().id(8).stageType(MODULE_8);

      final List<StageType> listOfStageTypes = List.of(
          stageType1, stageType2, stageType3, stageType4,
          stageType5, stageType6, stageType7, stageType8);

      final var stageTypesRead = new StageTypesRead().stageTypes(listOfStageTypes);

      when(stageService.getStageTypes()).thenReturn(stageTypesRead);

      final String expectedJson = new ObjectMapper().writeValueAsString(stageTypesRead);

      mockMvc
        .perform(get("/stages/types")
            .with(validCATJwtReqPostProcessor))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(content().contentType(APPLICATION_JSON))
        .andExpect(content().string(expectedJson));
    }

    @Test
    public void shouldReturn401ForGetStageTypesWithMissingJWT() throws Exception {
      mockMvc
        .perform(get("/stages/types"))
        .andDo(print())
        .andExpect(status().isUnauthorized());
    }

    @Test
    public void shouldReturnCorrectStageTypesForValidEventId() throws Exception {
        final var stagesRead = new StagesRead()
                .eventId(EVENT_ID)
                .numberOfStages(2)
                .stages(List.of(new Stages().id(1),
                                new Stages().id(2)));

        when(stageService.getStagesForEventId(EVENT_ID)).thenReturn(stagesRead);

        final String expectedJson = new ObjectMapper().writeValueAsString(stagesRead);

        mockMvc
          .perform(get("/stages/event/" + EVENT_ID)
              .with(validCATJwtReqPostProcessor))
          .andDo(print())
          .andExpect(status().isOk())
          .andExpect(content().contentType(APPLICATION_JSON))
          .andExpect(content().string(expectedJson));
    }

    @Test
    public void shouldReturn401ForGetStageTypesForEventWithMissingJWT() throws Exception {
      mockMvc
        .perform(get("/stages/event/" + EVENT_ID))
        .andDo(print())
        .andExpect(status().isUnauthorized());
    }

    @Test
    public void shouldHandleNoMatchOnEventId() throws Exception {
      final var stagesRead = new StagesRead()
              .eventId("NoMatchEventId")
              .numberOfStages(0)
              .stages(null);

      when(stageService.getStagesForEventId("NoMatchEventId")).thenReturn(stagesRead);

      final String expectedJson = new ObjectMapper().writeValueAsString(stagesRead);

      mockMvc
          .perform(get("/stages/event/NoMatchEventId")
              .with(validCATJwtReqPostProcessor))
          .andDo(print())
          .andExpect(status().isOk())
          .andExpect(content().contentType(APPLICATION_JSON))
          .andExpect(content().string(expectedJson));
    }

    @Test
    public void shouldCreateStagesForValidEventId() throws Exception {
      when(stageService.createOrUpdateStagesForEventId(EVENT_ID, stagesWrite)).thenReturn(true);

      mockMvc
        .perform(post("/stages/event/" + EVENT_ID)
            .with(validCATJwtReqPostProcessor)
            .contentType(MediaType.APPLICATION_JSON)
            .content(stageRequestJson))
        .andDo(print())
        .andExpect(status().isOk());
    }

    @Test
    public void shouldReturn401ForCreateStagesWithMissingJWT() throws Exception {
      mockMvc
        .perform(post("/stages/event/" + EVENT_ID)
            .contentType(MediaType.APPLICATION_JSON)
            .content(stageRequestJson)
            .accept(APPLICATION_JSON))
        .andDo(print())
        .andExpect(status().isUnauthorized());
    }

    @Test
    public void shouldHandleCreateStagesForNullStageIds() throws Exception {

      final var stageRequestWithNullStages = new StagesWrite().stages(null);

      when(stageService.createOrUpdateStagesForEventId(EVENT_ID, stageRequestWithNullStages))
          .thenThrow(new StageException("Cannot save stage data, invalid data for eventId: " + EVENT_ID));

      final String requestJson = new ObjectMapper().writeValueAsString(stageRequestWithNullStages);

      mockMvc
        .perform(post("/stages/event/" + EVENT_ID)
            .with(validCATJwtReqPostProcessor)
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestJson)
            .accept(APPLICATION_JSON))
        .andDo(print())
        .andExpect(status().isInternalServerError())
        .andExpect(content().contentType(APPLICATION_JSON))
        .andExpect(jsonPath("$.errors[0].detail", containsString("Cannot save stage data, invalid data for eventId: " + EVENT_ID)));
    }

    @Test
    public void shouldHandleCreateStagesForEmptyStageIds() throws Exception {
      final var stageRequestWithEmptyStages = new StagesWrite().stages(List.of());

      when(stageService.createOrUpdateStagesForEventId(EVENT_ID, stageRequestWithEmptyStages))
          .thenThrow(new StageException("Cannot save stage data, invalid data for eventId: " + EVENT_ID));

      final String requestJson = new ObjectMapper().writeValueAsString(stageRequestWithEmptyStages);

      mockMvc
        .perform(post("/stages/event/" + EVENT_ID, requestJson)
            .with(validCATJwtReqPostProcessor)
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestJson)
            .accept(APPLICATION_JSON))
        .andDo(print())
        .andExpect(status().isInternalServerError())
        .andExpect(content().contentType(APPLICATION_JSON))
        .andExpect(jsonPath("$.errors[0].detail", containsString("Cannot save stage data, invalid data for eventId: " + EVENT_ID)));
    }
}
