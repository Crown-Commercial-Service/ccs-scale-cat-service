package uk.gov.crowncommercial.dts.scale.cat.model.agreements;

import lombok.Data;

/**
 * Lot detail (minimal)
 */
@Data
public class LotDetail {

  /**
   * Lot number e.g. "1" for Lot 1.
   */
  private String number;

  /**
   * Lot name e.g. "Finance".
   */
  private String name;

  /**
   * Short textual description of the Lot.
   */
  private String description;

  /**
   * Type of the lot.
   */
  private LotType type;

}
