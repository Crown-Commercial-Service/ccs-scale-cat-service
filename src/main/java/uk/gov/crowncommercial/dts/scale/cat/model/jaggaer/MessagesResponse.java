package uk.gov.crowncommercial.dts.scale.cat.model.jaggaer;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class MessagesResponse {
  Integer returnCode;
  String returnMessage;
  Integer totRecords;
  Integer returnedRecords;
  Integer startAt;
  MessageList messageList;
}

