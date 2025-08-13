package uk.gov.crowncommercial.dts.scale.cat.controller;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.interceptors.TrackExecutionTime;
import uk.gov.crowncommercial.dts.scale.cat.service.NotificationService;

import java.util.Map;

/**
 * Email Controller which provides email sending API using GOV.UK Notify
 */
@RestController
@RequestMapping(path = "/emails", produces = APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Slf4j
@Validated
public class EmailController extends AbstractRestController {

    private final NotificationService notificationService;

    /**
     * Send an email using a GOV.UK Notify template
     * 
     * @param templateId the GOV.UK Notify template ID
     * @param targetEmail the recipient email address
     * @param placeholders map of template placeholders
     * @param reference optional reference string
     * @param authentication JWT authentication token
     * @return ResponseEntity with success message
     */
    @PostMapping("/send")
    @TrackExecutionTime
    public ResponseEntity<Map<String, Object>> sendEmail(
            @RequestParam String templateId,
            @RequestParam String targetEmail,
            @RequestBody Map<String, Object> placeholders,
            @RequestParam(required = false) String reference,
            final JwtAuthenticationToken authentication) {

        var principal = getPrincipalFromJwt(authentication);
        log.info("sendEmail invoked on behalf of principal: {} to recipient: {} using template: {}", 
                principal, targetEmail, templateId);

        try {
            notificationService.sendEmail(templateId, targetEmail, placeholders, reference);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Email sent successfully",
                "templateId", templateId,
                "targetEmail", targetEmail
            ));

        } catch (Exception e) {
            log.error("Failed to send email to {} using template {}", targetEmail, templateId, e);
            
            return ResponseEntity.badRequest()
                .body(Map.of(
                    "success", false,
                    "message", "Failed to send email: " + e.getMessage(),
                    "templateId", templateId,
                    "targetEmail", targetEmail
                ));
        }
    }
} 