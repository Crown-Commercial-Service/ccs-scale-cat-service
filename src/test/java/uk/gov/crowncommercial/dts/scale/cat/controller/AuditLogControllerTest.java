package uk.gov.crowncommercial.dts.scale.cat.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
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
import uk.gov.crowncommercial.dts.scale.cat.model.entity.audit.AuditLog;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.audit.AuditLogDto;
import uk.gov.crowncommercial.dts.scale.cat.service.AuditLogService;
import uk.gov.crowncommercial.dts.scale.cat.utils.TendersAPIModelUtils;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuditLogController.class)
@Import({TendersAPIModelUtils.class, JaggaerAPIConfig.class, ApplicationFlagsConfig.class, OAuth2Config.class})
@ActiveProfiles("test")
class AuditLogControllerTest {

    private static final String GET_AUDIT_LOG_PATH = "/audit/logs";
    private static final String ADD_AUDIT_LOG_PATH = "/audit";
    private static final String PRINCIPAL = "jsmith@ccs.org.uk";
    private static final String FROM_DATE_PARAMS = "fromDate";
    private static final String FROM_DATE_VALUE = "2025-09-10 10:09";
    private static final String TO_DATE_PARAMS = "toDate";
    private static final String TO_DATE_VALUE = "2025-07-28 16:05";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuditLogService auditLogService;

    @InjectMocks
    private AuditLogController auditLogController;

    private SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor validJwtReqPostProcessor;

    @Autowired
    private ObjectMapper objectMapper;

    private AuditLogDto auditLogDto;
    private AuditLog auditLog;

    @BeforeEach
    void setUp() {

        auditLogDto = new AuditLogDto();
        auditLogDto.fromDate = "2025-09-01";
        auditLogDto.toDate = "2025-09-10";
        auditLogDto.auditLogDetails = "Test audit log";

        auditLog = new AuditLog();
        auditLog.setBeforeUpdate("2025-09-01");
        auditLog.setAfterUpdate("2025-09-10");
        auditLog.setReason("Test audit log");
        auditLog.setUpdatedBy("tester");
        auditLog.setTimestamp(Timestamp.valueOf(LocalDateTime.now()));

        validJwtReqPostProcessor = jwt().authorities(new SimpleGrantedAuthority("CAT_USER"))
                .jwt(jwt -> jwt.subject(PRINCIPAL));
    }

    @Test
    void getAllLogs_ShouldReturnAuditLogs() throws Exception {
        // Arrange
        List<AuditLogDto> mockLogs = List.of(auditLogDto);
        when(auditLogService.getAuditLogsWithDate(any(), any()))
                .thenReturn(mockLogs);

        // Act & Assert
        mockMvc.perform(get(GET_AUDIT_LOG_PATH).with(validJwtReqPostProcessor)
                        .param(FROM_DATE_PARAMS, FROM_DATE_VALUE)
                        .param(TO_DATE_PARAMS, TO_DATE_VALUE)
                        .accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].fromDate").value(auditLogDto.fromDate))
                .andExpect(jsonPath("$[0].toDate").value(auditLogDto.toDate))
                .andExpect(jsonPath("$[0].auditLogDetails").value(auditLogDto.auditLogDetails));

        verify(auditLogService, times(1)).getAuditLogsWithDate(any(), any());
    }


    @Test
    void createLog_ShouldReturnSavedAuditLog() throws Exception {
        // Arrange
        when(auditLogService.save(any(AuditLog.class))).thenReturn(auditLog);

        // Act & Assert
        mockMvc.perform(post(ADD_AUDIT_LOG_PATH).with(validJwtReqPostProcessor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(auditLog)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.beforeUpdate").value(auditLog.getBeforeUpdate()))
                .andExpect(jsonPath("$.afterUpdate").value(auditLog.getAfterUpdate()))
                .andExpect(jsonPath("$.reason").value(auditLog.getReason()));

        verify(auditLogService, times(1)).save(any(AuditLog.class));
    }

    // --- Negative / Edge Scenarios ---

    @Test
    void getAllLogs_ShouldReturnEmptyList_WhenNoLogsExist() throws Exception {
        when(auditLogService.getAuditLogsWithDate(any(), any())).thenReturn(Collections.emptyList());

        mockMvc.perform(get(GET_AUDIT_LOG_PATH).with(validJwtReqPostProcessor)
                        .param(FROM_DATE_PARAMS, FROM_DATE_VALUE)
                        .param(TO_DATE_PARAMS, TO_DATE_VALUE)
                        .accept(APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isEmpty());

        verify(auditLogService, times(1)).getAuditLogsWithDate(any(), any());
    }


    @Test
    void createLog_ShouldReturnBadRequest_WhenRequestBodyIsInvalid() throws Exception {
        // Sending invalid JSON
        String invalidJson = "{ invalid json }";

        mockMvc.perform(post(ADD_AUDIT_LOG_PATH).with(validJwtReqPostProcessor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());

        verify(auditLogService, never()).save(any());
    }

    @Test
    void createLog_ShouldHandleNullFieldsGracefully() throws Exception {
        AuditLog nullFieldsLog = new AuditLog(); // all fields null
        when(auditLogService.save(any(AuditLog.class))).thenReturn(nullFieldsLog);

        mockMvc.perform(post(ADD_AUDIT_LOG_PATH).with(validJwtReqPostProcessor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(nullFieldsLog)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.beforeUpdate").doesNotExist())
                .andExpect(jsonPath("$.afterUpdate").doesNotExist())
                .andExpect(jsonPath("$.reason").doesNotExist());

        verify(auditLogService, times(1)).save(any(AuditLog.class));
    }
}