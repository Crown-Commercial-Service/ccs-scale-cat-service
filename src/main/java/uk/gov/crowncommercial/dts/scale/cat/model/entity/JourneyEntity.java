package uk.gov.crowncommercial.dts.scale.cat.model.entity;

import java.time.Instant;
import java.util.List;
import javax.persistence.*;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import lombok.*;
import lombok.experimental.FieldDefaults;
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
@TypeDef(name = "jsonb", typeClass = JsonBinaryType.class)
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

  @Type(type = "jsonb")
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
