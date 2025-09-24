package uk.gov.crowncommercial.dts.scale.cat.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.audit.AuditLog;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.audit.AuditLogDto;
import uk.gov.crowncommercial.dts.scale.cat.repo.AuditLogRepo;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isEmpty;

/**
 * Audit log service to handle CRUD operations
 */
@Service
public class AuditLogService {

    /**
     * Following three columns can not be null as database expect values.
     * Therefore, if caller passed null or empty, service should insert following default values.
     */
    private static final String DEFAULT_AUDIT_REASON = "No audit reason specified";
    private static final String DEFAULT_AUDIT_FROM_URL = "No audit from url specified";
    private static final String DEFAULT_AUDIT_UPDATED_BY = "No audit reason specified";

    private final DateTimeFormatter formatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    @Autowired
    private AuditLogRepo repository;


    public List<AuditLogDto> getAuditLogsWithDate(String fromDate,
                                                  String toDate) {

        return repository
                .findAll()
                .stream()
                .map(this::mapModelToDto)
                .toList();
    }

    private AuditLogDto mapModelToDto(AuditLog auditLog) {

        AuditLogDto dto = new AuditLogDto();
        dto.updatedBy = auditLog.getUpdatedBy();
        dto.formUrl = auditLog.getFormUrl();
        dto.reason = auditLog.getReason();
        dto.beforeUpdate = auditLog.getBeforeUpdate();
        dto.afterUpdate = auditLog.getAfterUpdate();
        dto.timestamp = auditLog.getTimestamp().toLocalDateTime().format(formatter);
        return dto;
    }

    public AuditLog save(AuditLog auditLog) {
        auditLog.setTimestamp(Timestamp.valueOf(LocalDateTime.now()));

        auditLog.setUpdatedBy(defaultIfBlank(auditLog.getUpdatedBy(), DEFAULT_AUDIT_UPDATED_BY));
        auditLog.setFormUrl(defaultIfBlank(auditLog.getFormUrl(), DEFAULT_AUDIT_FROM_URL));
        auditLog.setReason(defaultIfBlank(auditLog.getReason(), DEFAULT_AUDIT_REASON));

        return repository.saveAndFlush(auditLog);
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return (value == null || value.isBlank()) ? defaultValue : value;
    }
}
