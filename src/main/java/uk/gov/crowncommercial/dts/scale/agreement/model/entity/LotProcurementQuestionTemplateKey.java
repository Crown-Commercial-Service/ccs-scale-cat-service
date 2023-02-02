package uk.gov.crowncommercial.dts.scale.agreement.model.entity;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import java.io.Serializable;

/**
 * Compound Key.
 */
@Data
@Embeddable
public class LotProcurementQuestionTemplateKey implements Serializable {

  private static final long serialVersionUID = 1L;

  @Column(name = "lot_id")
  Integer lotId;

  @Column(name = "template_id")
  Integer templateId;

  @Column(name = "procurement_event_type_id")
  Integer procurementEventTypeId;
}
