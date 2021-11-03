package uk.gov.crowncommercial.dts.scale.cat.model.jaggaer;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;
import lombok.extern.jackson.Jacksonized;

@Value
@Jacksonized
@Builder
public class RfxSetting {

  String rfxId;
  String rfxReferenceCode;
  Integer rfiFlag;
  String tenderReferenceCode;
  String templateReferenceCode;
  @NonFinal
  String shortDescription;
  String longDescription;
  String rankingStrategy;
  BuyerCompany buyerCompany;
  OwnerUser ownerUser;
  String rfxType;
  String status;
  Integer statusCode;

  public void setShortDescription(final String shortDescription) {
    this.shortDescription = shortDescription;
  }
}
