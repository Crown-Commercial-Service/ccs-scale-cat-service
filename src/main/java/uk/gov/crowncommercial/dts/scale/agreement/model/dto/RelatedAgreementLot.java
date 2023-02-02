package uk.gov.crowncommercial.dts.scale.agreement.model.dto;

import lombok.Data;

/**
 * A simple reference to a related Agreement/Lot combination and relationship type.
 */
@Data
public class RelatedAgreementLot {

  /**
   * Commercial Agreement number.
   */
  private String caNumber;

  /**
   * Lot number within the Commercial Agreement/
   */
  private String lotNumber;

  /**
   * The type of the relationship in machine readable form, e.g.
   * FurtherCompetitionWhenBudgetExceeded
   */
  private String relationship;
}
