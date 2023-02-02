package uk.gov.crowncommercial.dts.scale.agreement.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Party role
 */
public enum PartyRole {

  @JsonProperty("buyer")
  BUYER,

  @JsonProperty("procuringEntity")
  PROCURING_ENTITY,

  @JsonProperty("supplier")
  SUPPLIER,

  @JsonProperty("tenderer")
  TENDERER,

  @JsonProperty("funder")
  FUNDER,

  @JsonProperty("enquirer")
  ENQUIRER,

  @JsonProperty("payer")
  PAYER,

  @JsonProperty("payee")
  PAYEE,

  @JsonProperty("reviewBody")
  REVIEW_BODY,

  @JsonProperty("interestedParty")
  INTERESTED_PARTY,

}
