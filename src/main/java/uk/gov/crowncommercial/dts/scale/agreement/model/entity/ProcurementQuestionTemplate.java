package uk.gov.crowncommercial.dts.scale.agreement.model.entity;

import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * Procurement Question Template.
 */
@Entity
@Immutable
@Table(name = "procurement_question_templates")
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
@TypeDef(name = "jsonb", typeClass = JsonBinaryType.class)
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE,region = "procurementQuestionTemplate")
public class ProcurementQuestionTemplate {

  @Id
  @Column(name = "template_id")
  Integer id;

  @Column(name = "template_name")
  String templateName;

  @Column (name= "template_description")
  String description;

  @Column (name ="template_parent")
  Integer parent;

  @Column (name = "template_mandatory")
  Boolean mandatory;

  @Column(name = "template_url")
  String templateUrl;

  @Type(type = "jsonb")
  @Column(name = "template_payload")
  Object templatePayload;

//  @Embedded
//  Timestamps timestamps;
}
