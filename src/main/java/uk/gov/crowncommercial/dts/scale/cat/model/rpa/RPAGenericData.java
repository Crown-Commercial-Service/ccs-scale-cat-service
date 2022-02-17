package uk.gov.crowncommercial.dts.scale.cat.model.rpa;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class RPAGenericData {
	String processInput;
	String processName;
	String profileName;
	String source;
	String sourceId;
	boolean retry;
	@JsonProperty(value = "isSync")
	boolean isSync;
	long requestTimeout;
}
