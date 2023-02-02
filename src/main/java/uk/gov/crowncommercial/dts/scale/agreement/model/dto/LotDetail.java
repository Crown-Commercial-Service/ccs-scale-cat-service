package uk.gov.crowncommercial.dts.scale.agreement.model.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.Collection;

/**
 * Lot Detail.
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
   * Effective start date of Lot.
   */
  private LocalDate startDate;

  /**
   * Effective end date of Lot.
   */
  private LocalDate endDate;

  /**
   * Short textual description of the Lot.
   */
  private String description;

  /**
   * Type of the lot.
   */
  private LotType type;

  /**
   * Routes to Market in lot.
   */
  private Collection<RouteToMarketDTO> routesToMarket;

  /**
   * A sector permitted to buy using the Agreement.
   */
  private Collection<String> sectors;

  /**
   * A simple reference to a related Agreement/Lot combination and relationship type.
   */
  private Collection<RelatedAgreementLot> relatedAgreementLots;

  /**
   * Buyer needs.
   */
  private Collection<BuyerNeed> buyerNeeds;

  /**
   * Rules.
   */
  private Collection<LotRuleDTO> rules;

}
