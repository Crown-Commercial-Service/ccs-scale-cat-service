package uk.gov.crowncommercial.dts.scale.cat.model.entity.ca;

import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.Type;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.Timestamps;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.Message;
import javax.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "message_task")
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

    @Column(name = "event_id")
    Integer eventType;

    @Embedded
    private Timestamps timestamps;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    MessageTaskStatus status;
}
