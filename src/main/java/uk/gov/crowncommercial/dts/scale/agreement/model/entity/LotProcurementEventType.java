package uk.gov.crowncommercial.dts.scale.agreement.model.entity;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.Immutable;

import javax.persistence.*;

/**
 * Lot Procurement Event Type.
 */
@Entity
@Immutable
@Table(name = "lot_procurement_event_types")
@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE,region = "lotProcurementEventTypes")
public class LotProcurementEventType {

  @EmbeddedId
  LotProcurementEventTypeKey key;

  @MapsId("procurementEventTypeId")
  @ManyToOne
  @JoinColumn(name = "procurement_event_type_id")
  @org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE,region = "procurementEventType")
  ProcurementEventType procurementEventType;

  @Column(name = "mandatory_event_ind")
  Boolean isMandatoryEvent;

  @Column(name = "repeatable_event_ind")
  Boolean isRepeatableEvent;

  @Column(name = "assessment_tool_id")
  String assessmentToolId;
}
