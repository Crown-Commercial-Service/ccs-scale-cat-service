package uk.gov.crowncommercial.dts.scale.cat.model.entity;

import java.time.Instant;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
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

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "project_id")
  ProcurementProject project;

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

  /**
   * Builds an instance from basic details
   *
   * @param jaggaerEventId The rfx reference code
   * @param principal
   * @return a procurement event
   */
  public static ProcurementEvent of(ProcurementProject project, String eventName,
      String jaggaerEventId, String ocdsAuthority, String ocidPrefix, String principal) {
    var procurementEvent = new ProcurementEvent();
    procurementEvent.setProject(project);
    procurementEvent.setEventName(eventName);
    procurementEvent.setOcdsAuthorityName(ocdsAuthority);
    procurementEvent.setOcidprefix(ocidPrefix);
    procurementEvent.setJaggaerEventId(jaggaerEventId);
    procurementEvent.setCreatedBy(principal); // Or Jaggaer user ID?
    procurementEvent.setCreatedAt(Instant.now());
    procurementEvent.setUpdatedBy(principal); // Or Jaggaer user ID?
    procurementEvent.setUpdatedAt(Instant.now());
    return procurementEvent;
  }

  public String getEventID() {
    return ocdsAuthorityName + "-" + ocidprefix + "-" + id;
  }
}
