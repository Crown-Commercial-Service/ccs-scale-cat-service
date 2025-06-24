package uk.gov.crowncommercial.dts.scale.cat.model.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "batching_queue")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BatchingQueueEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "request_id")
    private Integer requestId;

    @Column(name = "user_id", nullable = false, length = 20)
    private String userId;

    @Column(name = "project_id", nullable = true)
    private Integer projectId;

    @Column(name = "event_id", nullable = true, length = 18)
    private String eventId;

    @Column(name = "request_url", nullable = false, length = 1000)
    private String requestUrl;

    @Column(name = "request_payload", nullable = false, columnDefinition = "jsonb")
    private String requestPayload;  // or JsonNode, see below

    @Column(name = "request_status", nullable = false, length = 12)
    private String request_status;

    @Column(name = "request_status_message", nullable = true)
    private String request_status_message;

    @Column(name = "request_attempts", nullable = false)
    private Integer request_attempts;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, updatable = true)
    private Instant updatedAt;
}