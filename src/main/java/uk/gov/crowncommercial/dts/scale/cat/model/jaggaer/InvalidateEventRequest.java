package uk.gov.crowncommercial.dts.scale.cat.model.jaggaer;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class InvalidateEventRequest {
  String invalidateReason;
  String rfxId;
  String rfxReferenceCode;
  OwnerUser operatorUser;
}
