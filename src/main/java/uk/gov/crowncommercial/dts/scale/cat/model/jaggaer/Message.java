package uk.gov.crowncommercial.dts.scale.cat.model.jaggaer;

import java.time.OffsetDateTime;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class Message {
  Integer messageId;
  Sender sender;
  OffsetDateTime sendDate;
  SenderUser senderUser;
  String subject;
  String body;
  @JsonProperty("object")
  MessageObject messageObject;
  Integer folderId;
  String direction;
  Integer isBroadcast;
  Integer parentMessageId;
  OffsetDateTime receiveDate;
  ReceiverList receiverList;
  ReadingList readingList;
  AttachmentList attachmentList;
  MessageCategory category;

}


@Value
@Builder
@Jacksonized
class MessageObject {
  String objectId;
  String objectReferenceCode;
  String objectTitle;
  String objectType;
  String objectSubType;
  String tenderCode;
  String tenderReferenceCode;
}

