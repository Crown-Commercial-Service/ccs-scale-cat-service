package uk.gov.crowncommercial.dts.scale.cat.model.jaggaer;

import java.time.OffsetDateTime;

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
  Object object;
  Integer folderId;
  String direction;
  Integer isBroadcast;
  OffsetDateTime receiveDate;
  ReceiverList receiverList;
  ReadingList readingList;
  AttachmentList attachmentList;

}

@Value
@Builder
@Jacksonized
class Object {
  String objectId;
  String objectReferenceCode;
  String objectTitle;
  String objectType;
  String objectSubType;
  String tenderCode;
  String tenderReferenceCode;
}

