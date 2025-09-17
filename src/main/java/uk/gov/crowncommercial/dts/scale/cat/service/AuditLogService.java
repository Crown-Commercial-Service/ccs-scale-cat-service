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

/**
 * Audit log service to handle CRUD operations
 */
@Service
public class AuditLogService {

    private final DateTimeFormatter formatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    @Autowired
    private AuditLogRepo repository;


    public List<AuditLogDto> getAuditLogsWithDate(String fromDate,
                                                  String toDate) {
        //TODO next part of the implementation we need to retrieve data
        // TODO based on the date passed, e.g. 'fromDate' and 'toDate'
        // TODO Also implement pagination

        return repository
                .findAll()
                .stream()
                .map(this::mapModelToDto)
                .toList();
    }

    private AuditLogDto mapModelToDto(AuditLog auditLog) {

        // TODO not sure what value to map with db field and response
        // TODO Following code need to revisit and populate correct field based on business requirement
        // TODO just populating random data for now
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
        return repository.saveAndFlush(auditLog);
    }
}
