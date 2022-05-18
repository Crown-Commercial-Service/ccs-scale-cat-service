package uk.gov.crowncommercial.dts.scale.cat.model.jaggaer;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class Offer {
  Integer supplierId;
  Integer offerState;
  Integer isWinner;
  Integer qualStatus;
  Integer techStatus;
  Integer commStatus;
  Double  techPoints;
  Integer ranking;
  TechOffer techOffer;
  Integer awardingRanking;
  Integer answerUserId;
  Integer answerRound;

}
