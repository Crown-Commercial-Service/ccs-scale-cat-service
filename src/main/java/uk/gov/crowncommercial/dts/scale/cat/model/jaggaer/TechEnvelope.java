package uk.gov.crowncommercial.dts.scale.cat.model.jaggaer;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class TechEnvelope {

  @JsonProperty("section")
  List<TechEnvelopeSection> sections;
}
