package uk.gov.crowncommercial.dts.scale.cat.dto;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import org.hibernate.type.descriptor.jdbc.NVarcharJdbcType;

import java.time.Instant;
import java.time.LocalDateTime;

public class AuditLogDTO {

    Integer log_id;
    String updated_by;
    String form_url;
    String reason;
    NVarcharJdbcType before_update;
    NVarcharJdbcType after_update;
    LocalDateTime timestamp;

    public Integer getLog_id() {
        return log_id;
    }

    public void setLog_id(Integer log_id) {
        this.log_id = log_id;
    }

    public String getUpdated_by() {
        return updated_by;
    }

    public void setUpdated_by(String updated_by) {
        this.updated_by = updated_by;
    }

    public String getForm_url() {
        return form_url;
    }

    public void setForm_url(String form_url) {
        this.form_url = form_url;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public NVarcharJdbcType getBefore_update() {
        return before_update;
    }

    public void setBefore_update(NVarcharJdbcType before_update) {
        this.before_update = before_update;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
