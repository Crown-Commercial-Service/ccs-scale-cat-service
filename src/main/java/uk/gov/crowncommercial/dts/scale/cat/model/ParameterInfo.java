package uk.gov.crowncommercial.dts.scale.cat.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class ParameterInfo {

    Integer parameterId;
    List<AttachmentInfo> attachmentInfoList;

}
