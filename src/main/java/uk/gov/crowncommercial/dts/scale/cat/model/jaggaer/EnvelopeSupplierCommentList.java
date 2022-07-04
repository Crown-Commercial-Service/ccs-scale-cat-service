package uk.gov.crowncommercial.dts.scale.cat.model.jaggaer;

import java.util.List;

import lombok.Builder;
import lombok.extern.jackson.Jacksonized;

@lombok.Value
@Builder
@Jacksonized
public class EnvelopeSupplierCommentList {

  List<EnvelopeSupplierComment> envelopeSupplierComment;
  
}
