package uk.gov.crowncommercial.dts.scale.cat.model;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AttachmentInfo {

    Integer parameterId;
    String attachmentId;
    String attachmentName;
    String secureToken;

}
