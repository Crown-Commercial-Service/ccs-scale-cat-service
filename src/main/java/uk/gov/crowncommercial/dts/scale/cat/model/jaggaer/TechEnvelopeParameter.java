package uk.gov.crowncommercial.dts.scale.cat.model.jaggaer;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class TechEnvelopeParameter {

  Long id;
  String name;
  String description;
  TechEnvelopeQuestionType type;
  Integer mandatory;
  Integer paramPos;
}
