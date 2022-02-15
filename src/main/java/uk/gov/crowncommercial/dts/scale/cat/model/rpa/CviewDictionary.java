package uk.gov.crowncommercial.dts.scale.cat.model.rpa;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
*
*/
@Value
@Builder
@Jacksonized
public class CviewDictionary {

	private String ErrorDescription;

	private String IsError;

	private String Status;
}
