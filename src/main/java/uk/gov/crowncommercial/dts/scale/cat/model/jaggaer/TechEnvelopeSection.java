package uk.gov.crowncommercial.dts.scale.cat.model.jaggaer;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class TechEnvelopeSection {

  String name;
  String type;
  Integer sectionPos;
  String questionType;
  TechEnvelopeParameterList parameterList;
}
