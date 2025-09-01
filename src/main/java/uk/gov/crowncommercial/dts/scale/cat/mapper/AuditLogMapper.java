package uk.gov.crowncommercial.dts.scale.cat.mapper;

import org.mapstruct.Mapper;
import org.springframework.stereotype.Component;
import uk.gov.crowncommercial.dts.scale.cat.dto.AuditLogDTO;
import uk.gov.crowncommercial.dts.scale.cat.model.audit.AuditLogModel;
import uk.gov.crowncommercial.dts.scale.cat.model.audit.AuditLogViewModel;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.AuditLogEntity;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;


@Mapper(componentModel = "spring")
@Component
public class AuditLogMapper {

    public AuditLogModel toAuditLogModel(AuditLogViewModel auditLogViewModel) {
        if (auditLogViewModel == null) {
            return null;
        }
        AuditLogModel auditLogModel = new AuditLogModel();
        auditLogModel.setFromDate(auditLogViewModel.getFromDate());
        auditLogModel.setToDate(auditLogViewModel.getToDateDate());
        auditLogModel.setAuditLogViewModel(auditLogViewModel.auditLogModel.getAuditLogViewModel());
        return auditLogModel;
    }

    public AuditLogDTO toAuditLogDto(AuditLogModel auditLogModel) {
        if (auditLogModel == null) return null;
        AuditLogDTO auditLogDTO = new AuditLogDTO();
        auditLogDTO.setTimestamp(auditLogModel.getFromDate());
        return auditLogDTO;
    }


    public AuditLogEntity toEntity(AuditLogDTO auditLogDTO) {
        if (auditLogDTO == null) return null;
        AuditLogEntity auditLogEntity = new AuditLogEntity();
        auditLogEntity.setTimestamp(auditLogDTO.getTimestamp());
        return auditLogEntity;
    }

    public void updateEntityFromDto(AuditLogDTO auditLogDTO, AuditLogEntity auditLogEntity) {
        if (auditLogDTO == null || auditLogEntity == null) return;

        if (auditLogDTO.getTimestamp() != null) {
            auditLogEntity.setTimestamp(auditLogDTO.getTimestamp());
        }
       }
}
