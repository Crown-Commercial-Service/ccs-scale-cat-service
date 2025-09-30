package uk.gov.crowncommercial.dts.scale.cat.model.entity.audit;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AuditLogDto {

    public String updatedBy;

    public String formUrl;

    public String reason;

    public String beforeUpdate;

    public String afterUpdate;

    public String timestamp;
}
