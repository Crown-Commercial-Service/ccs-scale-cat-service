package uk.gov.crowncommercial.dts.scale.cat.service;

import static org.springframework.util.CollectionUtils.isEmpty;
import java.net.URI;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.config.RPAAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.exception.AuthorisationFailureException;
import uk.gov.crowncommercial.dts.scale.cat.exception.JaggaerRPAException;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.OrganisationMapping;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.*;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.MessageRequestInfo;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.Receiver;
import uk.gov.crowncommercial.dts.scale.cat.model.rpa.*;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;

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

  public static final String JAGGAER_USER_NOT_FOUND = "Jaggaer user not found";

  private final WebClient rpaServiceWebClient;

  private final WebclientWrapper webclientWrapper;

  private final ValidationService validationService;

  private final UserProfileService userService;

  private final ObjectMapper objectMapper;

  private final RPAAPIConfig rpaAPIConfig;

  private final JaggaerService jaggaerService;

  private final UserProfileService userProfileService;

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
  public String sendOrRespondMessage(final String profile, final Integer projectId,
      final String eventId, final Message message) {
    var procurementEvent = validationService.validateProjectAndEventIds(projectId, eventId);
    var buyerUser = userService.resolveBuyerUserByEmail(profile);
    var ocds = message.getOCDS();
    var nonOCDS = message.getNonOCDS();

    // Creating RPA process input string
    var inputBuilder = RPAProcessInput.builder().userName(buyerUser.get().getEmail())
        .password(rpaAPIConfig.getBuyerPwd()).ittCode(procurementEvent.getExternalReferenceId())
        .broadcastMessage(nonOCDS.getIsBroadcast() ? "Yes" : "No").messagingAction(CREATE_MESSAGE)
        .messageSubject(ocds.getTitle()).messageBody(ocds.getDescription())
        .messageClassification(nonOCDS.getClassification().getValue()).senderName("")
        .supplierName("").messageReceivedDate("");

    // To reply the message
    if (nonOCDS.getParentId() != null) {
      var messageDetails = jaggaerService.getMessage(nonOCDS.getParentId());
      if (messageDetails == null) {
        throw new JaggaerRPAException("ParentId not found: " + nonOCDS.getParentId());
      }
      String messageRecievedDate = messageDetails.getReceiveDate().format(DATE_TIME_FORMATTER);
      log.info("MessageRecievedDate: {}", messageRecievedDate);
      inputBuilder.messagingAction(RESPOND_MESSAGE).messageReceivedDate(messageRecievedDate)
          .senderName(messageDetails.getSender().getName());
    }

    // Adding supplier details
    if (Boolean.FALSE.equals(nonOCDS.getIsBroadcast())) {
      if (CollectionUtils.isEmpty(nonOCDS.getReceiverList())) {
        throw new JaggaerRPAException("Suppliers are mandatory if broadcast is 'No'");
      }
      var supplierString = validateSuppliers(procurementEvent, nonOCDS);
      log.info("Suppliers list: {}", supplierString);
      inputBuilder.supplierName(supplierString);
    }

    return callRPAMessageAPI(inputBuilder.build());
  }

  /**
   * @param procurementEvent
   * @param nonOCDS
   * @return suppliers as a string
   */
  private String validateSuppliers(final ProcurementEvent procurementEvent,
      final MessageNonOCDS nonOCDS) {
    // ignoring string content from organisation Ids
    var supplierOrgIds = nonOCDS.getReceiverList().stream()
        .map(ls -> ls.getId().replace("GB-COH-", "")).collect(Collectors.toSet());

    // Retrieve and verify Tenders DB org mappings
    var supplierOrgMappings =
        retryableTendersDBDelegate.findOrganisationMappingByOrganisationIdIn(supplierOrgIds);
    if (isEmpty(supplierOrgMappings)) {
      var errorDesc =
          String.format("No supplier organisation mappings found in Tenders DB %s", supplierOrgIds);
      log.error(errorDesc);
      throw new JaggaerRPAException(errorDesc);
    }

    // Find all externalOrgIds
    var supplierExternalIds = supplierOrgMappings.stream()
        .map(OrganisationMapping::getExternalOrganisationId).collect(Collectors.toSet());

    // Find actual suppliers of project and event
    var suppliers = jaggaerService.getRfx(procurementEvent.getExternalEventId()).getSuppliersList()
        .getSupplier();

    // Find all unmatched org ids
    var unMatchedSuppliers = supplierExternalIds.stream()
        .filter(orgid -> suppliers.stream()
            .noneMatch(supplier -> supplier.getCompanyData().getId().equals(orgid)))
        .collect(Collectors.toList());

    if (!isEmpty(unMatchedSuppliers)) {
      var errorDesc =
          String.format("Supplied organisation mappings not matched with actual suppliers '%s'",
              unMatchedSuppliers);
      log.error(errorDesc);
      throw new JaggaerRPAException(errorDesc);
    }

    // Comparing the requested organisation ids and event suppliers info
    var matchedSuppliers = suppliers.stream()
        .filter(supplier -> supplierExternalIds.stream()
            .anyMatch(orgId -> orgId.equals(supplier.getCompanyData().getId())))
        .collect(Collectors.toList());

    // Coverting all requested suppliers names into a string
    var appendSupplierList = new StringBuilder();
    matchedSuppliers.stream().forEach(supplier -> {
      appendSupplierList.append(supplier.getCompanyData().getName());
      appendSupplierList.append(";");
    });

    return appendSupplierList.toString().substring(0, appendSupplierList.toString().length() - 1);
  }

  /**
   * @param processInput
   * @return rpa status
   * @throws JsonProcessingException
   */
  @SneakyThrows
  private String callRPAMessageAPI(final RPAProcessInput processInput) {
    var request = new RPAGenericData();
    request.setProcessInput(objectMapper.writeValueAsString(processInput))
        .setProcessName(RPAProcessNameEnum.BUYER_MESSAGING.getValue())
        .setProfileName(rpaAPIConfig.getProfileName()).setSource(rpaAPIConfig.getSource())
        .setSourceId(rpaAPIConfig.getSourceId()).setRequestTimeout(rpaAPIConfig.getRequestTimeout())
        .setSync(true);
    log.info("RPA Request: {}", objectMapper.writeValueAsString(request));

    var response =
        webclientWrapper.postDataWithToken(request, RPAAPIResponse.class, rpaServiceWebClient,
            rpaAPIConfig.getTimeoutDuration(), rpaAPIConfig.getAccessUrl(), getAccessToken());

    return validateResponse(response);
  }

  /**
   * Validate RPA API Response
   *
   * @param apiResponse
   * @return rpa api status
   */
  @SuppressWarnings("unchecked")
  @SneakyThrows
  private String validateResponse(final RPAAPIResponse apiResponse) {
    var convertedObject = convertStringToObject(apiResponse.getResponse().getResponse());
    var maps = (List<Map<String, String>>) convertedObject.get("AutomationOutputData");
    var automationData = objectMapper.readValue(objectMapper.writeValueAsString(maps.get(1)),
        AutomationOutputData.class);

    var status = automationData.getCviewDictionary().getStatus();
    log.info("Status of RPA API call : {} ", status);

    if (automationData.getCviewDictionary().getIsError().contentEquals("True")) {
      var errorDescription = automationData.getCviewDictionary().getErrorDescription();
      log.info("Error Description {} ", errorDescription);
      throw new JaggaerRPAException(errorDescription);
    }
    return status;
  }

  /**
   * Convert String to Object
   *
   * @param inputString
   * @return Object
   */
  @SneakyThrows
  private Map<String, Object> convertStringToObject(final String inputString) {
    return objectMapper.readValue(inputString, new TypeReference<HashMap<String, Object>>() {});
  }

  /**
   * Get Access Token by calling RPA access API
   *
   * @return accessToken
   */
  private String getAccessToken() {
    var jaggerRPACredentials = new HashMap<String, String>();
    jaggerRPACredentials.put("username", rpaAPIConfig.getUserName());
    jaggerRPACredentials.put("password", rpaAPIConfig.getUserPwd());
    var uriTemplate = rpaAPIConfig.getAuthenticationUrl();
    return webclientWrapper.postData(jaggerRPACredentials, String.class, rpaServiceWebClient,
        rpaAPIConfig.getTimeoutDuration(), uriTemplate);
  }

  /**
   * Returns a list of message summary at the event level.
   *
   * @param messageRequestInfo
   * @return
   */
  public MessageSummary getMessagesSummary(final MessageRequestInfo messageRequestInfo) {

    var jaggaerUserId = userProfileService
        .resolveBuyerUserByEmail(messageRequestInfo.getPrincipal())
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

    var messagesResponse =
        jaggaerService.getMessages(event.getExternalReferenceId(), messageRequestInfo.getPage());
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

    // convert to message summary
    return new MessageSummary()
        .counts(new MessageTotals().messagesTotal(messagesResponse.getTotRecords())
            .pageTotal(messagesResponse.getReturnedRecords()))
        .links(getLinks(messages, messageRequestInfo.getPageSize()))
        .messages(getCatMessages(messages, messageRequestInfo.getMessageRead(), jaggaerUserId,
            messageRequestInfo.getPageSize()));
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

    userProfileService.resolveBuyerUserByEmail(principal)
        .orElseThrow(() -> new AuthorisationFailureException(JAGGAER_USER_NOT_FOUND)).getUserId();
    validationService.validateProjectAndEventIds(procId, eventId);
    var reponse = jaggaerService.getMessage(messageId);

    return new uk.gov.crowncommercial.dts.scale.cat.model.generated.Message()
        .OCDS(getMessageOCDS(reponse)).nonOCDS(getMessageNonOCDS(reponse));
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
      read = message.getReceiverList().getReceiver().stream().anyMatch(receiverPredicate);
    }

    return new CaTMessage().OCDS(getCaTMessageOCDS(message)).nonOCDS(new CaTMessageNonOCDS()
        .direction(CaTMessageNonOCDS.DirectionEnum.fromValue(message.getDirection())).read(read));
  }

  private CaTMessageOCDS getCaTMessageOCDS(
      final uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.Message message) {
    return new CaTMessageOCDS().date(message.getSendDate()).id(message.getMessageId())
        .title(message.getSubject()).author(new CaTMessageOCDSAllOfAuthor()
            .id(message.getSender().getId()).name(message.getSender().getName()));
  }

  private MessageOCDS getMessageOCDS(
      final uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.Message message) {
    return new MessageOCDS().date(message.getSendDate()).id(message.getMessageId())
        .title(message.getSubject()).author(new CaTMessageOCDSAllOfAuthor()
            .id(message.getSender().getId()).name(message.getSender().getName()));
  }

  private MessageNonOCDS getMessageNonOCDS(
      final uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.Message message) {
    // TODO fix generated class with correct classes
    return new MessageNonOCDS()
        .direction(MessageNonOCDS.DirectionEnum.fromValue(message.getDirection()))
        // .attachments( message.getAttachmentList().getAttachment().stream()
        // .map(object -> new MessageNonOCDSAllOfAttachments()).collect(Collectors.toList()))
        .readList(message.getReadingList().getReading().stream()
            .map(reading -> new ContactPoint1().name(reading.getReaderName()))
            .collect(Collectors.toList()))
        .receiverList(message.getReceiverList().getReceiver().stream().map(
            receiver -> new OrganizationReference1().id(receiver.getId()).name(receiver.getName()))
            .collect(Collectors.toList()));
  }
}
