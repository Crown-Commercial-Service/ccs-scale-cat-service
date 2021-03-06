package uk.gov.crowncommercial.dts.scale.cat.model.jaggaer;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class RfxSetting {

  String rfxId;
  String rfxReferenceCode;
  Integer rfiFlag;
  String tenderReferenceCode;
  String templateReferenceCode;
  String shortDescription;
  String longDescription;
  String rankingStrategy;
  BuyerCompany buyerCompany;
  OwnerUser ownerUser;
  String rfxType;
}
