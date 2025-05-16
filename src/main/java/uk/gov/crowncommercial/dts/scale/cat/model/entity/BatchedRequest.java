package uk.gov.crowncommercial.dts.scale.cat.model.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.Rfx;

/**
 * Entity for a single Jaegger request stored within the batching solution
 */
@Entity
@Table(name = "batching_queue")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BatchedRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "request_id")
    Integer id;

    @Column(name = "user_id")
    String userId;

    @Column(name = "request_url")
    String requestUrl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_payload")
    Rfx requestPayload;
}