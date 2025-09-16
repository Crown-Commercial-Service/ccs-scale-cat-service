package uk.gov.crowncommercial.dts.scale.cat.mapper;

import org.mapstruct.Mapper;
import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.crowncommercial.dts.scale.cat.dto.AuditLogDTO;
import uk.gov.crowncommercial.dts.scale.cat.model.audit.AuditLogModel;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.AuditLogEntity;

@Mapper(componentModel = "spring")
@Component
public class AuditLogMapper {

    @Autowired
    ModelMapper auditLogMapper;

    public AuditLogDTO convertAuditLogEntityToAuditLogDto(AuditLogEntity auditLogEntity, AuditLogDTO auditLogDTO) {
        if (auditLogEntity == null) return null;
        auditLogMapper.getConfiguration()
                      .setMatchingStrategy(MatchingStrategies.LOOSE);
        auditLogDTO = new AuditLogDTO();
        auditLogDTO.setTimestamp(auditLogEntity.getTimestamp());
        auditLogMapper.map(auditLogEntity, AuditLogDTO.class);
        return auditLogDTO;
    }

}
