package uk.gov.crowncommercial.dts.scale.cat.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.Builder;
import java.time.LocalDateTime;

@Entity(name = "audit_log")
@Table(name = "audit_log")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AuditLogEntity {
    @Id
    @Column(name = "log_id")
    Integer log_id;

    @Column(name = "updated_by")
    String updated_by;

    @Column(name = "form_url")
    String form_url;

    @Column(name = "reason")
    String reason;

    @Column(name = "before_update", columnDefinition = "varchar2")
    String before_update;

    @Column(name = "after_update", columnDefinition = "varchar2")
    String after_update;

    @Column(name = "timestamp")
    LocalDateTime timestamp;

}