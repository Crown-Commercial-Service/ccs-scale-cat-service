package uk.gov.crowncommercial.dts.scale.cat.model.entity.ca;

import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.Type;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.Message;
import javax.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "assessments")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MessageTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "message_id")
    Integer messageId;

    @Type(type = "jsonb")
    @Column(name = "message_request")
    Message messageRequest;

    @Column(name = "event_type")
    String eventType;

    @Column(name = "created_by", updatable = false)
    String createdBy;

    @Column(name = "created_at", updatable = false)
    Instant createdAt;

    @Column(name = "updated_by")
    String updatedBy;

    @Column(name = "updated_at")
    Instant updatedAt;

    @Column(name = "status")
    String status;
}
