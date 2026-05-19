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

        stageRequestJson = new ObjectMapper().writeValueAsString(stagesWrite);
    }

    // TODO - BM - NCAS-844 - fixme

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
              .numberOfStages(0);

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

      final var stageRequestWithNullStages = new StagesWrite();

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
      final var stageRequestWithEmptyStages = new StagesWrite();

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
