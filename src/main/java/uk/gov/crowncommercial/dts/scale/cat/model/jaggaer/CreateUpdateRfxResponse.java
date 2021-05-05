package uk.gov.crowncommercial.dts.scale.cat.model.jaggaer;

import lombok.Data;

/**
 *
 */
@Data
public class CreateUpdateRfxResponse {

  int returnCode;
  String returnMessage;
  /**
   * Event ID, e.g. rfq_53220
   */
  String rfxId;
  /**
   * Event code, e.g. itt_1880
   */
  String rfxReferenceCode;
}
