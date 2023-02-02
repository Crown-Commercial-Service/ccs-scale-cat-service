package uk.gov.crowncommercial.dts.scale.agreement.model.dto;

import lombok.Data;

/**
 * Data required from the relevant transaction to be able to evaluate a rule.
 */
@Data
public class TransactionData {

  /**
   * Name of the variable.
   */
  private String name;

  /**
   * Path or other location of the data which can be evaluated by the application.
   */
  private String location;
}
