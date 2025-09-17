package uk.gov.crowncommercial.dts.scale.cat.model.entity.audit;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AuditLogDto {
    public String fromDate;

    public String toDate;

    public String auditLogDetails;
}
