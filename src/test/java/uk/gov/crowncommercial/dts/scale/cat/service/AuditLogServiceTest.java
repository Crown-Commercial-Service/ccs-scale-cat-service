package uk.gov.crowncommercial.dts.scale.cat.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.audit.AuditLog;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.audit.AuditLogDto;
import uk.gov.crowncommercial.dts.scale.cat.repo.AuditLogRepo;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    private static final String FROM_DATE = "2025-09-13T10:00";
    private static final String TO_DATE = "2025-09-16T16:12";

    private final DateTimeFormatter formatterForParamsDate =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    @Mock
    private AuditLogRepo repository;

    @InjectMocks
    private AuditLogService service;

    private AuditLog auditLog;

    private Timestamp timestamp;

    @BeforeEach
    void setUp() {
        timestamp = Timestamp.valueOf(LocalDateTime.now());
        auditLog = new AuditLog();
        auditLog.setUpdatedBy("Rob");
        auditLog.setFormUrl("http://localhost:8092");
        auditLog.setBeforeUpdate("{\"email\":\"old@example.com\"}");
        auditLog.setAfterUpdate("{\"email\":\"new@example.com\"}");
        auditLog.setReason("Updated email");
        auditLog.setTimestamp(timestamp);
    }

    @Test
    void getAuditLogsWithDate_ShouldReturnMappedDtos() {

        // Arrange
        when(repository.findByTimestampBetweenOrderByTimestampDesc(
                Timestamp.valueOf(LocalDateTime.parse(FROM_DATE, formatterForParamsDate)),
                Timestamp.valueOf(LocalDateTime.parse(TO_DATE, formatterForParamsDate))))
                .thenReturn(List.of(auditLog));

        // Act
        List<AuditLogDto> result = service.getAuditLogsWithDate(FROM_DATE, TO_DATE);

        // Assert
        assertEquals(1, result.size());
        AuditLogDto dto = result.get(0);
        assertEquals("Rob", dto.updatedBy);
        assertEquals("http://localhost:8092", dto.formUrl);
        assertEquals("{\"email\":\"old@example.com\"}", dto.beforeUpdate);
        assertEquals("{\"email\":\"new@example.com\"}", dto.afterUpdate);
        assertEquals("Updated email", dto.reason);

        verify(repository, times(1))
                .findByTimestampBetweenOrderByTimestampDesc(
                        Timestamp.valueOf(LocalDateTime.parse(FROM_DATE, formatterForParamsDate)),
                        Timestamp.valueOf(LocalDateTime.parse(TO_DATE, formatterForParamsDate)));
    }

    @Test
    void save_ShouldSetTimestampAndCallRepository() {
        // Arrange
        when(repository.saveAndFlush(any(AuditLog.class))).thenAnswer(invocation -> {
            AuditLog saved = invocation.getArgument(0);
            saved.setLogId(123); // simulate DB generated ID
            return saved;
        });

        // Act
        AuditLog savedLog = service.save(auditLog);

        // Assert
        assertNotNull(savedLog.getTimestamp(), "Timestamp should be set before save");
        assertEquals(123, savedLog.getLogId());
        assertEquals("Rob", savedLog.getUpdatedBy());

        verify(repository, times(1)).saveAndFlush(auditLog);
    }
}