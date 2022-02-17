package uk.gov.crowncommercial.dts.scale.cat.model.rpa;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Jacksonized
@Builder
public class RPAResponse {
	String response;
}
