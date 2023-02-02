package uk.gov.crowncommercial.dts.scale.agreement.model.entity;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.Immutable;

import javax.persistence.*;

/**
 * Procurement Event Type.
 */
@Entity
@Immutable
@Table(name = "template_group_mapping")
@Setter
@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TemplateGroupMapping {

  @Id
  @Column(name = "template_group_mapping_id")
  Integer id;

  @ToString.Exclude
  @ManyToOne
  @JoinColumn(name = "template_group_id")
  TemplateGroup templateGroup;

  @ToString.Exclude
  @ManyToOne
  @JoinColumn(name = "template_id")
  ProcurementQuestionTemplate template;

//  @ToString.Exclude
//  @OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
//  @JoinColumn(name = "template_group_id")


  @ToString.Exclude
  @Embedded
  private Timestamps timestamps;
}
