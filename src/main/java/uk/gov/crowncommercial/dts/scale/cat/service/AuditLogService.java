package uk.gov.crowncommercial.dts.scale.cat.service;

import org.springframework.stereotype.Service;
import uk.gov.crowncommercial.dts.scale.cat.dto.AuditLogDTO;
import uk.gov.crowncommercial.dts.scale.cat.mapper.AuditLogMapper;
import uk.gov.crowncommercial.dts.scale.cat.model.audit.AuditLogModel;
import uk.gov.crowncommercial.dts.scale.cat.repo.AuditLogRepo;
import java.time.LocalDateTime;

@Service
public class AuditLogService {

    private final AuditLogMapper auditLogMapper;
    private final AuditLogRepo auditLogRepo;

    public AuditLogService(AuditLogRepo auditLogRepo, AuditLogMapper auditLogMapper) {
        this.auditLogRepo = auditLogRepo;
        this.auditLogMapper = auditLogMapper;
    }


    public AuditLogDTO getAuditLogDTO(LocalDateTime fromDate, LocalDateTime toDate) {
        AuditLogDTO auditLogDto = new AuditLogDTO();
        AuditLogModel auditLogModel = new AuditLogModel();
        auditLogModel.setFromDate(fromDate);
        auditLogModel.setToDate(toDate);
        auditLogDto = auditLogMapper.convertAuditLogModelToAuditLogDto(auditLogModel);
        return auditLogDto;
       }
}

