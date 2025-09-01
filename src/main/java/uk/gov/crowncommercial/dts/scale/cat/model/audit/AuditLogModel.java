package uk.gov.crowncommercial.dts.scale.cat.model.audit;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AuditLogModel {
    private LocalDateTime fromDate;
    private LocalDateTime toDate;
    private AuditLogViewModel auditLogViewModel;
}
