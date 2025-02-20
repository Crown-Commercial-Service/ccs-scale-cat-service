package uk.gov.crowncommercial.dts.scale.cat.service;

import static java.lang.String.format;
import static org.springframework.util.CollectionUtils.isEmpty;
import java.net.URI;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.ObjectUtils;
import uk.gov.crowncommercial.dts.scale.cat.exception.AuthorisationFailureException;
import uk.gov.crowncommercial.dts.scale.cat.exception.JaggaerRPAException;
import uk.gov.crowncommercial.dts.scale.cat.exception.ResourceNotFoundException;
import uk.gov.crowncommercial.dts.scale.cat.model.DocumentAttachment;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.*;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.Message;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.MessageNonOCDS.ClassificationEnum;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.*;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;

import static uk.gov.crowncommercial.dts.scale.cat.config.Constants.ERR_MSG_JAGGAER_USER_NOT_FOUND;
import static uk.gov.crowncommercial.dts.scale.cat.config.Constants.UNLIMITED_VALUE;
/**
 *
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MessageService {

  private static final String CREATE_MESSAGE = "Create";
  private static final String RESPOND_MESSAGE = "Respond";
  private static final DateTimeFormatter DATE_TIME_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.000+00:00");
  private static final String RPA_DELIMETER = "~|";
  private static final String OBJECT_TYPE = "RFQ";

  public static final String JAGGAER_USER_NOT_FOUND = "Jaggaer user not found";
  static final String ERR_MSG_FMT_CONCLAVE_USER_ORG_MISSING =
      "Organisation [%s] not found in Conclave";
  static final String ERR_MSG_FMT_ORG_MAPPING_MISSING = "Organisation [%s] not found in OrgMapping";
  static final String ERR_MSG_DOC_NOT_FOUND = "Document [%s] not found in message attachments list";
  private final ValidationService validationService;
  private final UserProfileService userService;
  private final JaggaerService jaggaerService;
  private final UserProfileService userProfileService;
  private final ConclaveService conclaveService;

  private final SupplierService supplierService;
  private final RetryableTendersDBDelegate retryableTendersDBDelegate;

  private static final String LINK_URI = "/messages/";
  private static final int RECORDS_PER_REQUEST = 100;

  
  /**
   * Which sends outbound message to all suppliers and single supplier. And also responds supplier
   * messages
   *
   * @param profile
   * @param projectId
   * @param eventId
   * @param message {@link Message}
   * @return
   * @throws JsonProcessingException
   */
  public String createOrReplyMessage(final String profile, final Integer projectId,
      final String eventId, final Message message) {
    var procurementEvent = validationService.validateProjectAndEventIds(projectId, eventId);
    var jaggaerUserId = userProfileService.resolveBuyerUserProfile(profile)
            .orElseThrow(() -> new AuthorisationFailureException(ERR_MSG_JAGGAER_USER_NOT_FOUND))
            .getUserId();
    var ocds = message.getOCDS();
    var nonOCDS = message.getNonOCDS();

    String messageClassification = nonOCDS.getClassification().getValue();
    if (messageClassification.contentEquals(ClassificationEnum.UNCLASSIFIED.getValue())) {
      messageClassification = "";
    }

    var messageRequest = CreateReplyMessage.builder().body(ocds.getDescription())
        .broadcast(Boolean.TRUE.equals(nonOCDS.getIsBroadcast()) ? "1" : "0")
        .messageClassificationCategoryName(messageClassification).subject(ocds.getTitle())
        .objectReferenceCode(procurementEvent.getExternalReferenceId()).objectType(OBJECT_TYPE)
        .operatorUser(OwnerUser.builder().id(jaggaerUserId).build());

    // Adding supplier details
    if (Boolean.FALSE.equals(nonOCDS.getIsBroadcast())) {
      if (CollectionUtils.isEmpty(nonOCDS.getReceiverList())) {
        throw new JaggaerRPAException("Suppliers are mandatory if broadcast is 'No'");
      }
      var suppliers = supplierService.getValidSuppliers(procurementEvent, nonOCDS.getReceiverList()
          .stream().map(OrganizationReference1::getId).toList());

      messageRequest.supplierList(SuppliersList.builder().supplier(suppliers.getFirst()).build());
    }

    // To reply the message
    if (nonOCDS.getParentId() != null) {
      var messageDetails = jaggaerService.getMessage(nonOCDS.getParentId());
      if (messageDetails == null) {
        throw new JaggaerRPAException("ParentId not found: " + nonOCDS.getParentId());
      }
      messageRequest.parentMessageId(String.valueOf(messageDetails.getMessageId()));
    }

    return jaggaerService.createReplyMessage(messageRequest.build()).getMessageId().toString();

  }


  /**
   * Returns a list of message summary at the event level.
   *
   * @param messageRequestInfo
   * @return
   */
  public MessageSummary getMessagesSummary(final MessageRequestInfo messageRequestInfo) {

    // REM Assumption that user is buyer only
    var jaggaerUserId = userProfileService
        .resolveBuyerUserProfile(messageRequestInfo.getPrincipal())
        .orElseThrow(() -> new AuthorisationFailureException(JAGGAER_USER_NOT_FOUND)).getUserId();

    var event = validationService.validateProjectAndEventIds(messageRequestInfo.getProcId(),
        messageRequestInfo.getEventId());

    Predicate<uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.Message> directionPredicate =
        message -> (MessageDirection.ALL.equals(messageRequestInfo.getMessageDirection())
            || message.getDirection().equals(messageRequestInfo.getMessageDirection().getValue()));
    Predicate<Receiver> receiverPredicate =
        receiver -> MessageRead.ALL.equals(messageRequestInfo.getMessageRead())
            || MessageRead.READ.equals(messageRequestInfo.getMessageRead())
                && receiver.getId().equals(jaggaerUserId);
   // var pageStart =  messageRequestInfo.getPage() == 1 ? 1 : ((messageRequestInfo.getPageSize() * (messageRequestInfo.getPage()-1)));
    var messagesResponse =
        jaggaerService.getMessages(event.getExternalReferenceId(), 1);
    var allMessages = messagesResponse.getMessageList().getMessage();

    /**
     * Make first request to jagger if total records are more than > 100 (Jaggaer returns max 100
     * order by date desc) and messageSort is TITLE/AUTHOR then make sub-sequent call to get total
     * records, then messageSort it send only requested no of records
     */
    if (MessageSort.AUTHOR.equals(messageRequestInfo.getMessageSort())
        || MessageSort.TITLE.equals(messageRequestInfo.getMessageSort())) {
      var totalRecords = messagesResponse.getTotRecords();
      var pages = Math.round(totalRecords / (double) RECORDS_PER_REQUEST);
      for (var i = 2; i < pages; i++) {
        var response =
            jaggaerService.getMessages(event.getExternalReferenceId(), i * RECORDS_PER_REQUEST);
        allMessages.addAll(response.getMessageList().getMessage());
      }
    }
    // Apply all predicates on messages
    var messages = allMessages.stream().filter(directionPredicate)
        .filter(message -> (isEmpty(message.getReceiverList().getReceiver())
            || message.getReceiverList().getReceiver().stream().anyMatch(receiverPredicate)))
        .collect(Collectors.toList());

    if (messages.isEmpty()) {
      return new MessageSummary();
    }
    // sort messages
    sortMessages(messages, messageRequestInfo.getMessageSort(),
        messageRequestInfo.getMessageSortOrder());
    int fromIndex = (messageRequestInfo.getPage() - 1) * messageRequestInfo.getPageSize();
    var pageMessages = fromIndex >= messages.size() ? Collections.<uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.Message>emptyList() : messages.subList(fromIndex, Math.min(fromIndex + messageRequestInfo.getPageSize(), messages.size()));
    // convert to message summary
    MessageSummary messageSummary=new MessageSummary().counts(
                    new MessageTotals()
                            .messagesTotal(messages.size())
                            .pageTotal((int)Math.ceil((double) messages.size()/messageRequestInfo.getPageSize())))
                            .messages(getCatMessages(pageMessages, messageRequestInfo.getMessageRead(), jaggaerUserId,
                            messageRequestInfo.getPageSize()));

    if( !UNLIMITED_VALUE.equals(messageRequestInfo.getPageSize().toString())){
      messageSummary.links(getLinks(messages, messageRequestInfo.getPageSize()));
    }
      return messageSummary;
  }

  private Links1 getLinks(
      final List<uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.Message> messages,
      final Integer pageSize) {
    var message = messages.iterator().next();
    return new Links1().first(URI.create(LINK_URI + message.getMessageId()))
        .self(URI.create(LINK_URI + message.getMessageId()))
        .prev(URI.create(LINK_URI + message.getMessageId()))
        .next(URI.create(LINK_URI + (message.getMessageId() + 1)))
        .last(URI.create(LINK_URI + (message.getMessageId() + pageSize)));
  }

  private void sortMessages(
      final List<uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.Message> messages,
      final MessageSort messageSort, final MessageSortOrder messageSortOrder) {

    Comparator<uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.Message> comparator;
    switch (messageSort) {
      case TITLE:
        comparator = Comparator
            .comparing(uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.Message::getSubject);
        break;
      case AUTHOR:
        comparator = Comparator.comparing(msg -> msg.getSender().getName());
        break;
      default:
        comparator = Comparator
            .comparing(uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.Message::getSendDate);
    }

    Collections.sort(messages,
        MessageSortOrder.ASCENDING.equals(messageSortOrder) ? comparator : comparator.reversed());
  }

  /**
   * Method to get details of message
   *
   * @param procId
   * @param eventId
   * @param messageId
   * @param principal
   * @return Message Summary
   */
  public uk.gov.crowncommercial.dts.scale.cat.model.generated.Message getMessageSummary(
      final Integer procId, final String eventId, final String messageId, final String principal) {

    var jaggaerUserId = userProfileService.resolveBuyerUserProfile(principal)
        .orElseThrow(() -> new AuthorisationFailureException(JAGGAER_USER_NOT_FOUND)).getUserId();
    ProcurementEvent procurementEvent = validationService.validateProjectAndEventIds(procId, eventId);
    var updateMessage = jaggaerService.updateMessage(MessageUpdate.builder().messageId(Integer.parseInt(messageId))
            .objectReferenceCode(procurementEvent.getExternalReferenceId()).objectType(OBJECT_TYPE).operatorUser(OwnerUser.builder().id(jaggaerUserId).build()).build());
    var response = jaggaerService.getMessage(messageId);

    return new uk.gov.crowncommercial.dts.scale.cat.model.generated.Message()
        .OCDS(getMessageOCDS(response)).nonOCDS(getMessageNonOCDS(response));
  }

  public DocumentAttachment downloadAttachment(final Integer procId, final String eventId,
      final String messageId, final String principal, final String documentId) {

    userProfileService.resolveBuyerUserProfile(principal)
        .orElseThrow(() -> new AuthorisationFailureException(JAGGAER_USER_NOT_FOUND)).getUserId();
    validationService.validateProjectAndEventIds(procId, eventId);

    log.debug("Requested messageId {} and documentId {}", messageId, documentId);
    var response = jaggaerService.getMessage(messageId);
    var eDocument = response.getAttachmentList().getAttachment().stream()
        .filter(doc -> doc.getFileId().equals(documentId)).findFirst().orElseThrow(
            () -> new ResourceNotFoundException(String.format(ERR_MSG_DOC_NOT_FOUND, documentId)));
    var docAttachment = jaggaerService.getDocument(Integer.parseInt(eDocument.getFileId()),
        eDocument.getFileName());

    return DocumentAttachment.builder().fileName(eDocument.getFileName())
        .contentType(docAttachment.getContentType()).data(docAttachment.getData()).build();
  }

  private List<CaTMessage> getCatMessages(
      final List<uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.Message> messages,
      final MessageRead messageRead, final String jaggaerUserId, final Integer pageSize) {
    return messages.stream()
        .map(message -> convertMessageToCatMessage(message, messageRead, jaggaerUserId))
        .limit(pageSize).collect(Collectors.toList());
  }

  private CaTMessage convertMessageToCatMessage(
      final uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.Message message,
      final MessageRead messageRead, final String jaggaerUserId) {

    final Predicate<Receiver> receiverPredicate =
        receiver -> MessageRead.READ.equals(messageRead) && receiver.getId().equals(jaggaerUserId);
    Boolean read;
    if (CaTMessageNonOCDS.DirectionEnum.SENT.getValue().equals(message.getDirection())) {
      read = Boolean.FALSE;
    } else {

      read = !ObjectUtils.isEmpty(message.getReadingList()) && !message.getReadingList().getReading().isEmpty();
    }

    return new CaTMessage().OCDS(getCaTMessageOCDS(message)).nonOCDS(new CaTMessageNonOCDS()
        .direction(CaTMessageNonOCDS.DirectionEnum.fromValue(message.getDirection())).read(read)
        .classification(
            uk.gov.crowncommercial.dts.scale.cat.model.generated.CaTMessageNonOCDS.ClassificationEnum
                .fromValue(message.getCategory() == null
                    ? uk.gov.crowncommercial.dts.scale.cat.model.generated.CaTMessageNonOCDS.ClassificationEnum.UNCLASSIFIED
                        .getValue()
                    : message.getCategory().getCategoryName()))
                    .isBroadcast((null==message.getIsBroadcast() || message.getIsBroadcast()<=0)?false:true )
                    .receiverList(message.getReceiverList().getReceiver().stream().map(
                                    receiver -> new OrganizationReference1().id(receiver.getId()).name(receiver.getName()))
                            .collect(Collectors.toList()))
            );
  }

  private CaTMessageOCDS getCaTMessageOCDS(
      final uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.Message message) {
    return new CaTMessageOCDS().date(message.getSendDate()).id(message.getMessageId())
        .title(message.getSubject()).author(getAuthorDetails(message));
  }

  private MessageOCDS getMessageOCDS(
      final uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.Message message) {
    return new MessageOCDS().date(message.getSendDate()).id(message.getMessageId())
        .title(message.getSubject()).description(message.getBody())
        .author(getAuthorDetails(message));
  }

  private CaTMessageOCDSAllOfAuthor getAuthorDetails(
      final uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.Message message) {
    CaTMessageOCDSAllOfAuthor author;
    if (CaTMessageNonOCDS.DirectionEnum.RECEIVED.getValue().equals(message.getDirection())) {
      // lookup org-mapping table by jaggaer orgId
      var supplierOrgMapping = retryableTendersDBDelegate
          .findOrganisationMappingByExternalOrganisationId(
              Integer.valueOf(message.getSender().getId()))
          .orElseThrow(() -> new ResourceNotFoundException(
              String.format(ERR_MSG_FMT_ORG_MAPPING_MISSING, message.getSender().getId())));

      author = new CaTMessageOCDSAllOfAuthor().id(supplierOrgMapping.getCasOrganisationId())
          .name(message.getSender().getName());
    } else {
      var jaggaerUser = userProfileService
          .resolveBuyerUserByUserId(String.valueOf(message.getSenderUser().getId()))
          .orElseThrow(() -> new ResourceNotFoundException(
              String.format("Jaggaer user not found for %s", message.getSenderUser().getId())));
      Optional<SSOCodeData.SSOCode> ssoCode = jaggaerUser.getSsoCodeData().getSsoCode().stream().findFirst();
      var conclaveUser = conclaveService.getUserProfile(ssoCode.isPresent()
                      && Objects.nonNull(ssoCode.get().getSsoUserLogin())
                      ? ssoCode.get().getSsoUserLogin() : jaggaerUser.getEmail())
              .orElseThrow(() -> new ResourceNotFoundException("Conclave"));
      var conclaveOrg = conclaveService.getOrganisationIdentity(conclaveUser.getOrganisationId())
          .orElseThrow(() -> new ResourceNotFoundException(
              format(ERR_MSG_FMT_CONCLAVE_USER_ORG_MISSING, conclaveUser.getOrganisationId())));
      author = new CaTMessageOCDSAllOfAuthor().name(conclaveOrg.getIdentifier().getLegalName())
          .id(conclaveOrg.getIdentifier().getScheme() + "-" + conclaveOrg.getIdentifier().getId());
    }
    return author;
  }

  private MessageNonOCDS getMessageNonOCDS(
      final uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.Message message) {
    // TODO fix generated class with correct classes
    return new MessageNonOCDS().parentId(String.valueOf(message.getParentMessageId()))
        .classification(ClassificationEnum
            .fromValue(message.getCategory() == null ? ClassificationEnum.UNCLASSIFIED.getValue()
                : message.getCategory().getCategoryName()))
        .direction(MessageNonOCDS.DirectionEnum.fromValue(message.getDirection()))
        .attachments(message.getAttachmentList().getAttachment().stream()
            .map(att -> new MessageNonOCDSAllOfAttachments().id(Integer.valueOf(att.getFileId()))
                .name(att.getFileName()).size(att.getFileSize()))
            .collect(Collectors.toList()))
        .readList(message.getReadingList().getReading().stream()
            .map(reading -> new ContactPoint1().name(reading.getReaderName()))
            .collect(Collectors.toList()))
        .receiverList(message.getReceiverList().getReceiver().stream().map(
            receiver -> new OrganizationReference1().id(receiver.getId()).name(receiver.getName()))
            .collect(Collectors.toList()));
  }
}
