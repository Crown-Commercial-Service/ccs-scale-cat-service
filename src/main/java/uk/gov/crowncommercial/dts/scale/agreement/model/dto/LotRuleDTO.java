package uk.gov.crowncommercial.dts.scale.agreement.model.dto;

import lombok.Data;

import java.util.Collection;

/**
 * Lot Rule.
 * 
 * Rules such as 'Prices may not rise more than {x} times in any consecutive {y} day period' can be
 * included in the 'other' element - in this case 2 data-points would be required.
 * 
 * In the above example:
 * 
 * name="max number of prices rises in period" ruleId="maxNumberPriceChangesInPeriod"
 * lotAttributes=[ {"numberOfTimes","integer",,3,},{"daysInPeriod" ,"integer",,7,}] transactionData=
 * ["priceRisesLastPeriod","product.priceRises"] evaluationType="complex"
 * 
 * as here specific logic is required to obtain the number of price rises before evaluating.
 */
@Data
public class LotRuleDTO {

  /**
   * Unique identifier of the rule.
   */
  private String ruleId;

  /**
   * Name of the rule (3 or 4 word description).
   */
  private String name;

  /**
   * Attributes.
   */
  private Collection<NameValueType> lotAttributes;

  /**
   * Data required from the relevant transaction to be able to evaluate the rule.
   */
  private Collection<TransactionData> transactionData;

  /**
   * Evaluation Type.
   */
  private EvaluationType evaluationType;

  /**
   * Name of the service to which the rule applies (in future the Agreement Service may only return
   * rules for the requested services)
   */
  private String service;
}
