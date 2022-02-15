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
public class AutomationOutputData {

	private CviewDictionary CviewDictionary;

	private String AppName;

}
