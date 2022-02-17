package uk.gov.crowncommercial.dts.scale.cat.model.rpa;

import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldDefaults;

@Data
@EqualsAndHashCode(callSuper = false)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RPAAPIResponse extends RPAGenericData {
	RetryConfiguartion retryConfigurations;
	String transactionId;
	String status;
	RPAResponse response;
	Object error;
}
