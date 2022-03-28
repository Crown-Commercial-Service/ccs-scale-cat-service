package uk.gov.crowncommercial.dts.scale.cat.model.jaggaer;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 *
 */
@Value
@Builder
@Jacksonized
public class WorkflowRfxResponse {

  Integer returnCode;
  String returnMessage;
  Integer finalStatusCode;
  String finalStatus;

}
