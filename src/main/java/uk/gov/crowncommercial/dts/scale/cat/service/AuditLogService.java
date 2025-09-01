package uk.gov.crowncommercial.dts.scale.cat.service;

import org.springframework.stereotype.Service;
import uk.gov.crowncommercial.dts.scale.cat.dto.AuditLogDTO;
import uk.gov.crowncommercial.dts.scale.cat.mapper.AuditLogMapper;
import uk.gov.crowncommercial.dts.scale.cat.model.audit.AuditLogModel;
import uk.gov.crowncommercial.dts.scale.cat.repo.AuditLogRepository;
import java.time.LocalDateTime;

@Service
public class AuditLogService {

    private final AuditLogMapper auditLogMapper;
    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository, AuditLogMapper auditLogMapper) {
        this.auditLogRepository = auditLogRepository;
        this.auditLogMapper = auditLogMapper;
    }


    public AuditLogDTO getAuditLogDTO(LocalDateTime fromDate, LocalDateTime toDate) {
        AuditLogDTO auditLogDto = new AuditLogDTO();
        AuditLogModel auditLogModel = new AuditLogModel();
        auditLogModel.setFromDate(fromDate);
        auditLogModel.setToDate(toDate);
        auditLogDto = auditLogMapper.toAuditLogDto(auditLogModel);
        return auditLogDto;
       }
}

