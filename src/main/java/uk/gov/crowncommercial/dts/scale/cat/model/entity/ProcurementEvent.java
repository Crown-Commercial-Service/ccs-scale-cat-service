package uk.gov.crowncommercial.dts.scale.cat.model.entity;

import java.time.Instant;
import javax.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldDefaults;

/**
 * JPA entity representing a mapping between a project event OCID (authority + prefix + internal ID)
 * and Jaggaer internal event code
 */
@Entity
@Table(name = "procurement_events")
@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
@EqualsAndHashCode(exclude = "project")
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
  String ocidPrefix;

  @Column(name = "external_event_id")
  String externalEventId;

  @Column(name = "external_reference_id")
  String externalReferenceId;

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
   * Builds an instance from basic details.
   * 
   * @param project the parent {@link ProcurementProject}
   * @param eventName Title/short description
   * @param externalEventId Rfx id
   * @param externalReferenceId Rfx reference code
   * @param ocdsAuthority Authority, e.g. 'osds'
   * @param ocidPrefix Prefix, e.g. 'b5fd17'
   * @param principal
   * @return a procurement event
   */
  public static ProcurementEvent of(ProcurementProject project, String eventName,
      String externalEventId, String externalReferenceId, String ocdsAuthority, String ocidPrefix,
      String principal) {
    var procurementEvent = new ProcurementEvent();
    procurementEvent.setProject(project);
    procurementEvent.setEventName(eventName);
    procurementEvent.setOcdsAuthorityName(ocdsAuthority);
    procurementEvent.setOcidPrefix(ocidPrefix);
    procurementEvent.setExternalEventId(externalEventId);
    procurementEvent.setExternalReferenceId(externalReferenceId);
    procurementEvent.setCreatedBy(principal); // Or Jaggaer user ID?
    procurementEvent.setCreatedAt(Instant.now());
    procurementEvent.setUpdatedBy(principal); // Or Jaggaer user ID?
    procurementEvent.setUpdatedAt(Instant.now());
    return procurementEvent;
  }

  public String getEventID() {
    return ocdsAuthorityName + "-" + ocidPrefix + "-" + id;
  }
}
