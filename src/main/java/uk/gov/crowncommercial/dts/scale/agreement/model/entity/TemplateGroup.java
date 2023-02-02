package uk.gov.crowncommercial.dts.scale.agreement.model.entity;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.Immutable;

import javax.persistence.*;
import java.util.Set;

/**
 * Procurement Event Type.
 */
@Entity
@Immutable
@Table(name = "template_groups")
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TemplateGroup {

  @Id
  @Column(name = "template_group_id")
  Integer id;

  @Column(name = "template_group_name")
  String name;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "lot_id")
  Lot lot;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "procurement_event_type_id")
  ProcurementEventType eventType;

  @EqualsAndHashCode.Exclude
  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(name = "template_group_mapping",
          joinColumns = @JoinColumn(name = "template_group_id"),
          inverseJoinColumns = @JoinColumn(name = "template_id"))
  Set<ProcurementQuestionTemplate> questionTemplates;

  @Embedded
  private Timestamps timestamps;
}
