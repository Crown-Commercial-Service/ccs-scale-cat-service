package uk.gov.crowncommercial.dts.scale.cat.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;

import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.crowncommercial.dts.scale.cat.service.NotificationService;
import uk.gov.service.notify.NotificationClientException;

@SpringBootTest(classes = {EmailController.class})
@ActiveProfiles("test")
class EmailControllerTest {

    @Autowired
    private EmailController emailController;

    @MockitoBean
    private NotificationService notificationService;

    private static final String TEST_TEMPLATE_ID = "test-template-123";
    private static final String TEST_EMAIL = "test@example.com";
    private static final String TEST_REFERENCE = "test-ref-123";

    @Test
    void testSendEmailSuccess() throws Exception {
        // Given
        Map<String, Object> placeholders = Map.of("name", "John Doe");
        JwtAuthenticationToken mockAuth = createMockJwtAuth();

        doNothing().when(notificationService).sendEmail(
            anyString(), anyString(), any(Map.class), anyString());

        // When
        ResponseEntity<Map<String, Object>> response = emailController.sendEmail(
            TEST_TEMPLATE_ID, TEST_EMAIL, placeholders, TEST_REFERENCE, mockAuth);

        // Then
        assertEquals(200, response.getStatusCodeValue());
        Map<String, Object> body = response.getBody();
        assertTrue((Boolean) body.get("success"));
        assertEquals("Email sent successfully", body.get("message"));
        assertEquals(TEST_TEMPLATE_ID, body.get("templateId"));
        assertEquals(TEST_EMAIL, body.get("targetEmail"));
    }

    @Test
    void testSendEmailFailure() throws Exception {
        // Given
        Map<String, Object> placeholders = Map.of("name", "John Doe");
        JwtAuthenticationToken mockAuth = createMockJwtAuth();

        doThrow(new RuntimeException("Test error"))
            .when(notificationService).sendEmail(anyString(), anyString(), any(Map.class), anyString());

        // When
        ResponseEntity<Map<String, Object>> response = emailController.sendEmail(
            TEST_TEMPLATE_ID, TEST_EMAIL, placeholders, TEST_REFERENCE, mockAuth);

        // Then
        assertEquals(400, response.getStatusCodeValue());
        Map<String, Object> body = response.getBody();
        assertFalse((Boolean) body.get("success"));
        assertEquals("Failed to send email: Test error", body.get("message"));
        assertEquals(TEST_TEMPLATE_ID, body.get("templateId"));
        assertEquals(TEST_EMAIL, body.get("targetEmail"));
    }

    @Test
    void testSendEmailWithoutReference() throws Exception {
        // Given
        Map<String, Object> placeholders = Map.of("name", "John Doe");
        JwtAuthenticationToken mockAuth = createMockJwtAuth();

        doNothing().when(notificationService).sendEmail(
            anyString(), anyString(), any(Map.class), anyString());

        // When
        ResponseEntity<Map<String, Object>> response = emailController.sendEmail(
            TEST_TEMPLATE_ID, TEST_EMAIL, placeholders, null, mockAuth);

        // Then
        assertEquals(200, response.getStatusCodeValue());
        Map<String, Object> body = response.getBody();
        assertTrue((Boolean) body.get("success"));
        assertEquals("Email sent successfully", body.get("message"));
    }

    private JwtAuthenticationToken createMockJwtAuth() {
        Jwt jwt = Jwt.withTokenValue("mock-token")
            .header("alg", "RS256")
            .claim("sub", "test-user")
            .claim("cii_org_id", "test-org")
            .build();
        return new JwtAuthenticationToken(jwt);
    }
} 