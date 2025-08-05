package uk.gov.crowncommercial.dts.scale.cat.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import uk.gov.crowncommercial.dts.scale.cat.service.NotificationService;
import uk.gov.service.notify.NotificationClientException;

@WebMvcTest(EmailController.class)
class EmailControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NotificationService notificationService;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String TEST_TEMPLATE_ID = "test-template-123";
    private static final String TEST_EMAIL = "test@example.com";
    private static final String TEST_REFERENCE = "test-ref-123";

    @BeforeEach
    void setUp() {
        // Setup any common test data
    }

    @Test
    @WithMockUser
    void testSendEmailSuccess() throws Exception {
        // Given
        Map<String, Object> placeholders = Map.of("name", "John Doe");

        doNothing().when(notificationService).sendEmail(
            anyString(), anyString(), any(Map.class), anyString());

        // When & Then
        mockMvc.perform(post("/emails/send")
                .param("templateId", TEST_TEMPLATE_ID)
                .param("targetEmail", TEST_EMAIL)
                .param("reference", TEST_REFERENCE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(placeholders)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Email sent successfully"))
                .andExpect(jsonPath("$.templateId").value(TEST_TEMPLATE_ID))
                .andExpect(jsonPath("$.targetEmail").value(TEST_EMAIL));
    }

    @Test
    @WithMockUser
    void testSendEmailFailure() throws Exception {
        // Given
        Map<String, Object> placeholders = Map.of("name", "John Doe");

        doThrow(new NotificationClientException("Test error"))
            .when(notificationService).sendEmail(anyString(), anyString(), any(Map.class), anyString());

        // When & Then
        mockMvc.perform(post("/emails/send")
                .param("templateId", TEST_TEMPLATE_ID)
                .param("targetEmail", TEST_EMAIL)
                .param("reference", TEST_REFERENCE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(placeholders)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Failed to send email: Test error"))
                .andExpect(jsonPath("$.templateId").value(TEST_TEMPLATE_ID))
                .andExpect(jsonPath("$.targetEmail").value(TEST_EMAIL));
    }

    @Test
    @WithMockUser
    void testSendEmailWithoutReference() throws Exception {
        // Given
        Map<String, Object> placeholders = Map.of("name", "John Doe");

        doNothing().when(notificationService).sendEmail(
            anyString(), anyString(), any(Map.class), anyString());

        // When & Then
        mockMvc.perform(post("/emails/send")
                .param("templateId", TEST_TEMPLATE_ID)
                .param("targetEmail", TEST_EMAIL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(placeholders)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Email sent successfully"));
    }
} 