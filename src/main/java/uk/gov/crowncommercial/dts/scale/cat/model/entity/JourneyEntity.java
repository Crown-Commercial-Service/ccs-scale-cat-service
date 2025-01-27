package uk.gov.crowncommercial.dts.scale.cat.model.entity;

import java.time.Instant;
import java.util.List;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.type.SqlTypes;
import uk.gov.crowncommercial.dts.scale.cat.model.journey_service.generated.JourneyStepState;

/**
 *
 */
@Entity
@Table(name = "journeys")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class JourneyEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "journey_id")
  Integer id;

  @Column(name = "client_id")
  String clientId;

  /**
   * E.g. Tenders event-id
   */
  @Column(name = "external_id")
  String externalId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "journey_details")
  List<JourneyStepState> journeyDetails;

  @Column(name = "created_by", updatable = false)
  String createdBy;

  @Column(name = "created_at", updatable = false)
  Instant createdAt;

  @Column(name = "updated_by")
  String updatedBy;

  @Column(name = "updated_at")
  Instant updatedAt;
}
