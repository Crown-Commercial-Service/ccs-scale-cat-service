package uk.gov.crowncommercial.dts.scale.cat.model.audit;

import java.time.LocalDateTime;

public class AuditLogViewModel {
    public LocalDateTime fromDate;
    public LocalDateTime toDate;
    public AuditLogModel auditLogModel;

    public AuditLogViewModel(LocalDateTime fromDate, LocalDateTime toDate, AuditLogModel auditLogModell) {
        this.fromDate = fromDate;
        this.toDate = toDate;
        this.auditLogModel = auditLogModell;
    }

    public LocalDateTime getFromDate() {
        return fromDate;
    }

    public LocalDateTime getToDateDate() {
        return toDate;
    }

    public void setToDate(LocalDateTime toDate) {
        this.toDate = toDate;
    }

    public void setFromDate(LocalDateTime fromDate) {
        this.fromDate = fromDate;
    }

    public AuditLogModel getAuditLogModel() {
        return auditLogModel;
    }

}


