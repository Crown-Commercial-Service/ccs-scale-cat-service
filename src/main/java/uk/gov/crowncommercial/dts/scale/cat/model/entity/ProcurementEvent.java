package uk.gov.crowncommercial.dts.scale.cat.model.entity;

import java.time.Instant;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

/**
 *
 */
@Entity
@Table(name = "procurement_events")
@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProcurementEvent {

  @Id
  @Column(columnDefinition = "uuid", name = "event_id")
  String id;

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
