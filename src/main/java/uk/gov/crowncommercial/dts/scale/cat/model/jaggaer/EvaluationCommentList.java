package uk.gov.crowncommercial.dts.scale.cat.model.jaggaer;

import lombok.Builder;
import lombok.extern.jackson.Jacksonized;

@lombok.Value
@Builder
@Jacksonized
public class EvaluationCommentList {

  EnvelopeSupplierCommentList envelopeSupplierCommentList;
}
