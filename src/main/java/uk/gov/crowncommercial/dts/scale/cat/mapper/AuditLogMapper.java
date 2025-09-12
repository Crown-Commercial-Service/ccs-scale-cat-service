package uk.gov.crowncommercial.dts.scale.cat.mapper;

import org.mapstruct.Mapper;
import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.crowncommercial.dts.scale.cat.dto.AuditLogDTO;
import uk.gov.crowncommercial.dts.scale.cat.model.audit.AuditLogModel;


@Mapper(componentModel = "spring")
@Component
public class AuditLogMapper {

    @Autowired
    ModelMapper auditLogMapper;

    public AuditLogDTO convertAuditLogModelToAuditLogDto(AuditLogModel auditLogModel) {
        if (auditLogModel == null) return null;
        auditLogMapper.getConfiguration()
                      .setMatchingStrategy(MatchingStrategies.LOOSE);
        AuditLogDTO auditLogDTO = new AuditLogDTO();
        auditLogDTO.setTimestamp(auditLogModel.getFromDate());
        auditLogMapper.map(auditLogModel, AuditLogDTO.class);
        return auditLogDTO;
    }
}
