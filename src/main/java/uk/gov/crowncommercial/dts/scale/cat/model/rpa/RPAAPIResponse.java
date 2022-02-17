package uk.gov.crowncommercial.dts.scale.cat.model.rpa;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RPAAPIResponse {
	String processInput;
	String processName;
	String profileName;
	String source;
	String sourceId;
	boolean retry;
	RetryConfiguartion retryConfigurations;
	long requestTimeout;
	@JsonProperty(value = "isSync")
	boolean isSync;
	String transactionId;
	String status;
	RPAResponse response;
	Object error;
}
