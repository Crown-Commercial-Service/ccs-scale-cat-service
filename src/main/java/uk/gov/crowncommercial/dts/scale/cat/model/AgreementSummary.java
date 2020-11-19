package uk.gov.crowncommercial.dts.scale.cat.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agreement Summary.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
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
