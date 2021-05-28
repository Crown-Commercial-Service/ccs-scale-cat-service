package uk.gov.crowncommercial.dts.scale.cat.model.entity;

import java.time.Instant;
import javax.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

/**
 * JPA entity representing a mapping between a project event OCID (authority + prefix + internal ID)
 * and Jaggaer internal event code
 */
@Entity
@Table(name = "procurement_events")
@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProcurementEvent {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "event_id")
  Integer id;

  @Column(name = "ocds_authority_name")
  String ocdsAuthorityName;

  @Column(name = "ocid_prefix")
  String ocidprefix;

  @Column(name = "jaggaer_event_id")
  String jaggaerEventId;

  @Column(name = "event_name")
  String eventName;

  @Column(name = "created_by", updatable = false)
  String createdBy;

  @Column(name = "created_at", updatable = false)
  Instant createdAt;

  @Column(name = "updated_by")
  String updatedBy;

  @Column(name = "updated_at")
  Instant updatedAt;

}
