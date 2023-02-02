package uk.gov.crowncommercial.dts.scale.agreement.model.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * Route to Market.
 */
@Data
public class RouteToMarketDTO {

  /**
   * Buying method.
   */
  private BuyingMethod buyingMethod;

  /**
   * The URL to use for the system to progress the procurement (e.g. BaT, CaT, other).
   */
  private String buyingSystemUrl;

  /**
   * The minimum value for which this Lot applies.
   */
  private BigDecimal minimumValue;

  /**
   * The maximum value for which this Lot applies.
   */
  private BigDecimal maximumValue;

  /**
   * PLACEHOLDER. Needs to be an anyOf for National:Regional or individual Regions or even lowest
   * level NUTS2
   */
  private String location;

  /**
   * Minimum contract length.
   */
  private ContractLength minContractLength;

  /**
   * Maximum contract length.
   */
  private ContractLength maxContractLength;

}
