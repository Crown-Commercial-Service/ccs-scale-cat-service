package uk.gov.crowncommercial.dts.scale.cat.model;

import lombok.Data;

/**
 * Agreement Summary.
 */
@Data
public class AgreementSummary {

  /**
   * Commercial Agreement Number, e.g. "RM1045".
   */
  private String number;

  /**
   * Commercial Agreement Name e.g. "Technology Products 2".
   */
  private String name;
}
