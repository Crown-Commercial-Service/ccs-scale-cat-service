package uk.gov.crowncommercial.dts.scale.agreement.model.entity;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.Immutable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * Procurement Event Type.
 */
@Entity
@Immutable
@Table(name = "procurement_event_types")
@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE,region = "procurementEventType")
public class ProcurementEventType {

  @Id
  @Column(name = "procurement_event_type_id")
  Integer id;

  @Column(name = "procurement_event_type_name")
  String name;

  @Column(name = "procurement_event_type_description")
  String description;

  @Column(name = "premarket_activity_ind")
  Boolean preMarketActivity;

}
