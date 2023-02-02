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
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Lot Rule Attribute.
 */
@Entity
@Immutable
@Table(name = "lot_rule_attributes")
@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE,region = "lotRuleAttributes")
public class LotRuleAttribute implements Serializable {

  private static final long serialVersionUID = 1L;

  @Id
  @Column(name = "lot_rule_id")
  Integer ruleId;

  @Id
  @Column(name = "attribute_name")
  String name;

  @Column(name = "attribute_data_type")
  String dataType;

  @Column(name = "value_number")
  BigDecimal valueNumber;

  @Column(name = "value_text")
  String valueText;

}
