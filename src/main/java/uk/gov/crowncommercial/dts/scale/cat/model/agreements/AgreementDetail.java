package uk.gov.crowncommercial.dts.scale.cat.model.agreements;

import lombok.Data;

import java.time.LocalDate;

/**
 * Agreement Detail.
 */
@Data
public class AgreementDetail {

  /**
   * Commercial Agreement Number e.g. "RM1045".
   */
  private String number;

  /**
   * Commercial Agreement Name e.g. "Technology Products 2".
   */
  private String name;

  /**
   * Short textual description of the commercial agreement.
   */
  private String description;

  /**
   * Effective start date of Commercial Agreement.
   */
  private LocalDate startDate;

  /**
   * Effective end date of Commercial Agreement.
   */
  private LocalDate endDate;

}