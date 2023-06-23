package uk.gov.crowncommercial.dts.scale.cat.model.jaggaer;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class MessageUpdate {
    Integer messageId;
    String objectReferenceCode;
    String objectType;
    OwnerUser operatorUser;
}
