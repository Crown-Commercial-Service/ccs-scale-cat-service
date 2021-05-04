package uk.gov.crowncommercial.dts.scale.cat.model.jaggaer;

import lombok.Data;

/**
 *
 */
@Data
public class CreateUpdateProjectResponse {

  int returnCode;

  String returnMessage;
  String tenderCode;
  String tenderReferenceCode;

}
