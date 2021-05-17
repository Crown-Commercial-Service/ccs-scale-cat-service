package uk.gov.crowncommercial.dts.scale.cat.model.jaggaer;

import lombok.Data;

@Data
public class CreateUpdateRfxResponse {

  int returnCode;
  String returnMessage;
  String rfxId;
  String rfxReferenceCode;
}
