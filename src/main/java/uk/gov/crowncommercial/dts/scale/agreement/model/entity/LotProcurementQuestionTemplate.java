package uk.gov.crowncommercial.dts.scale.agreement.model.entity;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.Immutable;

import javax.persistence.*;

/**
 * Lot Procurement Question Template.
 */
@Entity
@Immutable
@Table(name = "lot_procurement_question_templates")
@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE,region = "lotProcurementQuestionTemplates")
public class LotProcurementQuestionTemplate {

  @EmbeddedId
  LotProcurementQuestionTemplateKey key;

  @MapsId("templateId")
  @ManyToOne
  @JoinColumn(name = "template_id")
  @org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE,region = "procurementQuestionTemplate")
  ProcurementQuestionTemplate procurementQuestionTemplate;

  @MapsId("procurementEventId")
  @ManyToOne
  @JoinColumn(name = "procurement_event_type_id")
  @org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE,region = "procurementEventType")
  ProcurementEventType procurementEventType;
}
