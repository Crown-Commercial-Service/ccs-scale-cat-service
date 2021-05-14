package uk.gov.crowncommercial.dts.scale.cat.model.jaggaer;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class Tender {

  String title;
  BuyerCompany buyerCompany;
  ProjectOwner projectOwner;
  String sourceTemplateReferenceCode;

}
