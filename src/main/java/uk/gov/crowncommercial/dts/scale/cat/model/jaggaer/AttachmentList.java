package uk.gov.crowncommercial.dts.scale.cat.model.jaggaer;

import java.util.List;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class AttachmentList {
  List<MessageObject> attachment;
}
