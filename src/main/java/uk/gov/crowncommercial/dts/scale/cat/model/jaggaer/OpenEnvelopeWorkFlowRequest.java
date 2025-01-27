package uk.gov.crowncommercial.dts.scale.cat.model.jaggaer;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@EqualsAndHashCode(callSuper = false)
public class OpenEnvelopeWorkFlowRequest extends RfxWorkflowRequest {

  private EnvelopeType envelopeType;

}
