package uk.gov.crowncommercial.dts.scale.cat.model.jaggaer;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class Tender {

  String tenderCode;
  String tenderReferenceCode;
  String title;
  BuyerCompany buyerCompany;
  ProjectOwner projectOwner;
  String sourceTemplateReferenceCode;
  String tenderStatusLabel;

}
