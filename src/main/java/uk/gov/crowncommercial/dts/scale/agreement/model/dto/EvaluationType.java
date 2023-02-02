package uk.gov.crowncommercial.dts.scale.agreement.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * How the rule should be evaluated - equal = rule is true if the lotAttribute is equal to the
 * transactionData greater = rule is true if the lotAttribute is greater than the transactionData
 * less = rule is true if the lotAttribute is less than the transactionData complex = rule specific
 * code is required to evaluate (typically where there are multiple variables) flag = rule is always
 * true. The presence of the rule is used to flag that certain behaviour is required. In some cases
 * data may be passed in the lotAttributes.
 * 
 */
public enum EvaluationType {

  @JsonProperty("equal")
  EQUAL,

  @JsonProperty("greater")
  GREATER,

  @JsonProperty("less")
  LESS,

  @JsonProperty("complex")
  COMPLEX,

  @JsonProperty("flag")
  FLAG
}
