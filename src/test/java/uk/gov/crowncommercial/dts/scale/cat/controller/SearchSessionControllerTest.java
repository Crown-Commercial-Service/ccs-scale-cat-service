package uk.gov.crowncommercial.dts.scale.cat.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.crowncommercial.dts.scale.cat.config.ApplicationFlagsConfig;
import uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.config.OAuth2Config;
import uk.gov.crowncommercial.dts.scale.cat.model.cas.generated.SearchSessionRead;
import uk.gov.crowncommercial.dts.scale.cat.model.cas.generated.SearchSessionWrite;
import uk.gov.crowncommercial.dts.scale.cat.service.SearchSessionService;
import uk.gov.crowncommercial.dts.scale.cat.utils.TendersAPIModelUtils;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@WebMvcTest(SearchSessionController.class)
@Import({TendersAPIModelUtils.class, JaggaerAPIConfig.class, ApplicationFlagsConfig.class, OAuth2Config.class})
@ActiveProfiles("test")
class SearchSessionControllerTest {

    private static SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor validCATJwtReqPostProcessor;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SearchSessionService searchSessionService;

    private static final String SEARCH_SESSION_URL = "/tenders/search-sessions";

    @BeforeAll
    public static void beforeEach() throws Exception {
        validCATJwtReqPostProcessor = jwt().authorities(new SimpleGrantedAuthority("CAT_USER"));
    }

    @Test
    public void testSaveSearchSessionOK() throws Exception {
        // Build the valid JSON payload using the new Write DTO
        SearchSessionWrite request = new SearchSessionWrite();
        request.setFilters("cloud-hosting,iaas,sc");
        request.setProjectId(123);
        request.setEventId("event-001");
        request.setCreatedBy("user@test.com");

        String generatedSessionId = UUID.randomUUID().toString();

        // Mock the service call
        when(searchSessionService.saveSearchSessionFilters(any(SearchSessionWrite.class)))
                .thenReturn(generatedSessionId);

        mockMvc.perform(post(SEARCH_SESSION_URL)
                        .contentType(MediaType.APPLICATION_JSON) // Use JSON instead of plain text
                        .content(objectMapper.writeValueAsString(request))
                        .with(validCATJwtReqPostProcessor))
                .andExpect(status().isOk())
                .andExpect(content().string(generatedSessionId));

        // Verify the Service was called and capture the object passed to it
        ArgumentCaptor<SearchSessionWrite> captor = ArgumentCaptor.forClass(SearchSessionWrite.class);
        verify(searchSessionService, times(1)).saveSearchSessionFilters(captor.capture());

        SearchSessionWrite capturedRequest = captor.getValue();
        assertEquals("cloud-hosting,iaas,sc", capturedRequest.getFilters());
        assertEquals(123, capturedRequest.getProjectId());
    }

    @Test
    public void testSaveSearchSessionEmptyPayloadReturnsBadRequest() throws Exception {
        // Create an empty request that fails validation
        SearchSessionWrite emptyRequest = new SearchSessionWrite();

        mockMvc.perform(post(SEARCH_SESSION_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(emptyRequest))
                        .with(validCATJwtReqPostProcessor))
                .andExpect(status().isBadRequest());

        // Verify the Service was completely protected from the bad request
        verify(searchSessionService, never()).saveSearchSessionFilters(any());
    }

    @Test
    public void testGetSearchSessionSuccess() throws Exception {
        String sessionId = "123e4567-e89b-12d3-a456-426614174000";

        // Build the Read DTO that the Service should return
        SearchSessionRead expectedResponse = new SearchSessionRead();
        expectedResponse.setSessionId(sessionId);
        expectedResponse.setFilters("cloud-hosting,iaas,sc");
        expectedResponse.setProjectId(123);
        expectedResponse.setEventId("event-001");
        expectedResponse.setCreatedBy("user@test.com");

        when(searchSessionService.getSearchSessionFilters(sessionId)).thenReturn(Optional.of(expectedResponse));

        mockMvc.perform(get(SEARCH_SESSION_URL + "/{sessionId}", sessionId)
                        .with(validCATJwtReqPostProcessor))
                .andExpect(status().isOk())
                // Assert the response is proper JSON and the fields map correctly
                .andExpect(jsonPath("$.sessionId").value(sessionId))
                .andExpect(jsonPath("$.filters").value("cloud-hosting,iaas,sc"))
                .andExpect(jsonPath("$.projectId").value(123))
                .andExpect(jsonPath("$.eventId").value("event-001"));
    }

    @Test
    public void testGetSearchSessionNotFound() throws Exception {
        String sessionId = "invalid-id";

        when(searchSessionService.getSearchSessionFilters(sessionId)).thenReturn(Optional.empty());

        mockMvc.perform(get(SEARCH_SESSION_URL + "/{sessionId}", sessionId)
                        .with(validCATJwtReqPostProcessor))
                .andExpect(status().isNotFound());
    }
}