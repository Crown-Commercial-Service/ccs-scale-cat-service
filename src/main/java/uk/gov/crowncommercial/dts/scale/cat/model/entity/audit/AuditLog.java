package uk.gov.crowncommercial.dts.scale.cat.model.entity.audit;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.Type;
import com.vladmihalcea.hibernate.type.json.JsonType;

import java.sql.Timestamp;

@Entity
@Table(name = "audit_log")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Auto-increment
    @Column(name = "log_id")
    Integer logId;

    @Column(name = "updated_by", length = 255)
    String updatedBy;

    @Column(name = "form_url", length = 255)
    String formUrl;

    @Column(name = "reason", length = 255)
    String reason;

    @Type(JsonType.class)
    @Column(name = "before_update", columnDefinition = "jsonb")
    String beforeUpdate;

    @Type(JsonType.class)
    @Column(name = "after_update", columnDefinition = "jsonb")
    String afterUpdate;

    @Column(name = "timestamp")
    Timestamp timestamp;
}