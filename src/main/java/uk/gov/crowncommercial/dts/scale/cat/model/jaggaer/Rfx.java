package uk.gov.crowncommercial.dts.scale.cat.model.jaggaer;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class Rfx {

  RfxSetting rfxSetting;
  RfxAdditionalInfoList rfxAdditionalInfoList;
  SuppliersList suppliersList;
  TechEnvelope techEnvelope;
  EmailRecipientList emailRecipientList;
  BuyerAttachmentsList buyerAttachmentsList;
  SellerAttachmentsList sellerAttachmentsList;
}
