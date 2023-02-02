package uk.gov.crowncommercial.dts.scale.agreement.model.entity;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.Immutable;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Set;

/**
 * Lot Rule.
 */
@Entity
@Immutable
@Table(name = "lot_rules")
@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE,region = "lotRules")
public class LotRule implements Serializable {

  private static final long serialVersionUID = 1L;

  @Id
  @Column(name = "lot_rule_id")
  Integer ruleId;

  @Column(name = "lot_id")
  Integer lotId;

  @Column(name = "lot_rule_name")
  String name;

  @Column(name = "evaluation_type")
  String evaluationType;

  @Column(name = "related_application_system_name")
  String service;

  @OneToMany
  @JoinColumn(name = "lot_rule_id")
  @org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE,region = "lotRuleTransactionData")
  private Set<LotRuleTransactionObject> transactionData;

  @OneToMany
  @JoinColumn(name = "lot_rule_id")
  @org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE,region = "lotRuleAttributes")
  private Set<LotRuleAttribute> lotAttributes;

}
