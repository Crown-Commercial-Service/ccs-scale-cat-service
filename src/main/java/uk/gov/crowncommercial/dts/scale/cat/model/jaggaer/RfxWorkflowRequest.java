package uk.gov.crowncommercial.dts.scale.cat.model.jaggaer;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
public class RfxWorkflowRequest {
  String rfxId;
  String rfxReferenceCode;
  OwnerUser operatorUser;
}
