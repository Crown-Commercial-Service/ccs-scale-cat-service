package uk.gov.crowncommercial.dts.scale.cat.model.rpa;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class RPAProcessInput {

	@JsonProperty("Search.Username")
	String userName;

	@JsonProperty("Search.Password")
	String password;

	@JsonProperty("Search.ITTCode")
	String ittCode;

	@JsonProperty("Search.BroadcastMessage")
	String broadcastMessage;

	@JsonProperty("Search.MessagingAction")
	String messagingAction;

	@JsonProperty("Search.MessageSubject")
	String messageSubject;

	@JsonProperty("Search.MessageBody")
	String messageBody;

	@JsonProperty("Search.MessageClassification")
	String messageClassification;

	@JsonProperty("Search.SenderName")
	String senderName;

	@JsonProperty("Search.SupplierName")
	String supplierName;

	@JsonProperty("Search.MessageReceivedDate")
	String messageReceivedDate;

}
