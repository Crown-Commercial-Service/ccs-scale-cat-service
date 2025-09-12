package uk.gov.crowncommercial.dts.scale.cat.service;

import org.springframework.stereotype.Service;
import uk.gov.crowncommercial.dts.scale.cat.dto.AuditLogDTO;
import uk.gov.crowncommercial.dts.scale.cat.mapper.AuditLogMapper;
import uk.gov.crowncommercial.dts.scale.cat.model.audit.AuditLogModel;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.AuditLogEntity;
import uk.gov.crowncommercial.dts.scale.cat.repo.AuditLogRepo;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class AuditLogService {

    private final AuditLogMapper auditLogMapper;
    private final AuditLogRepo auditLogRepo;
    private List<AuditLogEntity> auditLogEntity;

    public AuditLogService(AuditLogRepo auditLogRepo, AuditLogMapper auditLogMapper, List <AuditLogEntity> auditLogEntity) {
        this.auditLogRepo = auditLogRepo;
        this.auditLogMapper = auditLogMapper;
        this.auditLogEntity = auditLogEntity;
    }


    public AuditLogDTO getAuditLogDTO(LocalDateTime fromDate, LocalDateTime toDate) {
        AuditLogDTO auditLogDto = new AuditLogDTO();
        AuditLogModel auditLogModel = new AuditLogModel();
        auditLogModel.setFromDate(fromDate);
        auditLogModel.setToDate(toDate);
        auditLogDto = auditLogMapper.convertAuditLogModelToAuditLogDto(auditLogModel);
        return auditLogDto;
       }

    public List <AuditLogEntity> getAuditLogEntity(LocalDateTime fromDate, LocalDateTime toDate) {
        auditLogEntity = auditLogRepo.findAuditLogEntitiesBy(fromDate, toDate);
        if (auditLogEntity != null)
            return auditLogEntity;
        return null;
    }

}

