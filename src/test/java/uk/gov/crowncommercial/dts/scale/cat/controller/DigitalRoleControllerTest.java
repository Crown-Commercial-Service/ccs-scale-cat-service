package uk.gov.crowncommercial.dts.scale.cat.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;

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
import uk.gov.crowncommercial.dts.scale.cat.model.DigitalRoleDTO;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.DigitalRole;
import uk.gov.crowncommercial.dts.scale.cat.service.DigitalRoleService;
import uk.gov.crowncommercial.dts.scale.cat.utils.TendersAPIModelUtils;

import java.util.List;
import java.util.Optional;

@WebMvcTest(DigitalRoleController.class)
@Import({
  TendersAPIModelUtils.class,
  JaggaerAPIConfig.class,
  ApplicationFlagsConfig.class,
  OAuth2Config.class
})
@ActiveProfiles("test")
class DigitalRoleControllerTest {

  @MockitoBean private DigitalRoleService digitalRoleService;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private MockMvc mockMvc;

  private static final String PRINCIPAL = "jsmith@ccs.org.uk";
  private SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor validJwtReqPostProcessor;

  @BeforeEach
  void beforeEach() {
    validJwtReqPostProcessor =
        jwt()
            .authorities(new SimpleGrantedAuthority("CAT_USER"))
            .jwt(jwt -> jwt.subject(PRINCIPAL));
  }

  @Test
  void testValidFindAllByProjectIdAndEventId() throws Exception {
    // Given
    final Long id = 1L;
    final String projectId = "12345";
    final String eventId = "ocds-pfhb7i-25306";
    final DigitalRole input =
        DigitalRole.builder()
            .id(id)
            .jobFamily("Architecture roles")
            .role("Business architect")
            .level("Trainee business architect")
            .count(10)
            .projectId(projectId)
            .eventId(eventId)
            .build();
    when(digitalRoleService.findAllByProjectIdAndEventId(projectId, eventId))
        .thenReturn(List.of(input));

    // When
    mockMvc
        .perform(
            get(String.format("/digitalRole/%s/%s", projectId, eventId))
                .with(validJwtReqPostProcessor))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(input.getId()))
        .andExpect(jsonPath("$[0].jobFamily").value(input.getJobFamily()))
        .andExpect(jsonPath("$[0].role").value(input.getRole()))
        .andExpect(jsonPath("$[0].level").value(input.getLevel()))
        .andExpect(jsonPath("$[0].projectId").value(input.getProjectId()))
        .andExpect(jsonPath("$[0].eventId").value(input.getEventId()));
  }

  @Test
  void testValidSave() throws Exception {
    // Given
    final Long id = 1L;
    final String projectId = "12345";
    final String eventId = "ocds-pfhb7i-25306";
    final DigitalRole input =
        DigitalRole.builder()
            .id(id)
            .jobFamily("Architecture roles")
            .role("Business architect")
            .level("Trainee business architect")
            .count(10)
            .projectId(projectId)
            .eventId(eventId)
            .build();
    when(digitalRoleService.saveAll(anyList())).thenReturn(List.of(input));

    // When
    mockMvc
        .perform(
            post("/digitalRole")
                .with(validJwtReqPostProcessor)
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(List.of(input))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(input.getId()))
        .andExpect(jsonPath("$[0].jobFamily").value(input.getJobFamily()))
        .andExpect(jsonPath("$[0].role").value(input.getRole()))
        .andExpect(jsonPath("$[0].level").value(input.getLevel()))
        .andExpect(jsonPath("$[0].projectId").value(input.getProjectId()))
        .andExpect(jsonPath("$[0].eventId").value(input.getEventId()));
  }

  @Test
  void testInvalidSave() throws Exception {
    // Given
    final DigitalRole input =
        DigitalRole.builder()
            .id(1L)
            .jobFamily(null)
            .role("Business architect")
            .level("Trainee business architect")
            .count(10)
            .projectId("12345")
            .eventId("ocds-pfhb7i-25306")
            .build();

    // When
    mockMvc
        .perform(
            post("/digitalRole")
                .with(validJwtReqPostProcessor)
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(List.of(input))))
        .andExpect(status().is5xxServerError());
  }

  @Test
  void testValidPut() throws Exception {
    // Given
    final Long id = 1L;
    final String projectId = "12345";
    final String eventId = "ocds-pfhb7i-25306";
    final DigitalRole input =
        DigitalRole.builder()
            .id(id)
            .jobFamily("Architecture roles")
            .role("Business architect")
            .level("Trainee business architect")
            .count(10)
            .projectId(projectId)
            .eventId(eventId)
            .build();
    when(digitalRoleService.findByIdIn(any())).thenReturn(List.of(input));
    when(digitalRoleService.saveAll(any())).thenReturn(List.of(input));

    // When
    mockMvc
        .perform(
            put("/digitalRole")
                .with(validJwtReqPostProcessor)
                .contentType(APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        List.of(
                            DigitalRoleDTO.builder()
                                .id(id)
                                .jobFamily("Job Family Test")
                                .role("Role Test")
                                .level("Level Test")
                                .count(9)
                                .projectId("Project Id Test")
                                .eventId("Event Id Test")
                                .build()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(input.getId()))
        .andExpect(jsonPath("$[0].jobFamily").value("Job Family Test"))
        .andExpect(jsonPath("$[0].role").value("Role Test"))
        .andExpect(jsonPath("$[0].level").value("Level Test"))
        .andExpect(jsonPath("$[0].count").value(9))
        .andExpect(jsonPath("$[0].projectId").value("Project Id Test"))
        .andExpect(jsonPath("$[0].eventId").value("Event Id Test"));
  }

  @Test
  void testValidDelete() throws Exception {
    // Given
    final Long id = 1L;
    final String projectId = "12345";
    final String eventId = "ocds-pfhb7i-25306";
    final DigitalRole input =
        DigitalRole.builder()
            .id(id)
            .jobFamily("Architecture roles")
            .role("Business architect")
            .level("Trainee business architect")
            .count(10)
            .projectId(projectId)
            .eventId(eventId)
            .build();
    when(digitalRoleService.findById(id)).thenReturn(Optional.of(input));
    doNothing().when(digitalRoleService).delete(input);

    // When
    mockMvc
        .perform(delete(String.format("/digitalRole/%s", id)).with(validJwtReqPostProcessor))
        .andExpect(status().isNoContent());
  }

  @Test
  void testInvalidDelete() throws Exception {
    // Given
    final Long id = 1L;
    when(digitalRoleService.findById(id)).thenReturn(Optional.empty());

    // When
    mockMvc
        .perform(delete(String.format("/digitalRole/%s", id)).with(validJwtReqPostProcessor))
        .andExpect(status().isNotFound());
  }
}
