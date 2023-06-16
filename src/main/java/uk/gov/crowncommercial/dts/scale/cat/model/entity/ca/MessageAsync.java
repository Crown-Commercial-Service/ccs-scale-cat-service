package uk.gov.crowncommercial.dts.scale.cat.model.entity.ca;

import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.Timestamps;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.Message;
import jakarta.persistence.*;

@Entity
@Table(name = "message_task")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MessageAsync {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "message_id")
    Integer messageId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "message_request")
    Message messageRequest;

    @Column(name = "event_id")
    Integer eventId;

    @Embedded
    private Timestamps timestamps;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    MessageTaskStatus status;
}
