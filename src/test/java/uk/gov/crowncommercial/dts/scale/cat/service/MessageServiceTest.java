package uk.gov.crowncommercial.dts.scale.cat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.Message.builder;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.web.reactive.function.client.WebClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.config.ApplicationFlagsConfig;
import uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.model.DocumentAttachment;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.OrganisationMapping;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementProject;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.Message;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.MessageDirection;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.MessageNonOCDS;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.MessageOCDS;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.MessageRead;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.MessageSort;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.MessageSortOrder;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.Attachment;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.AttachmentList;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.CompanyData;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.CreateReplyMessage;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.ExportRfxResponse;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.MessageCategory;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.MessageList;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.MessageRequestInfo;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.MessageResponse;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.MessagesResponse;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.Receiver;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.ReceiverList;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.Sender;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.SenderUser;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.SubUsers.SubUser;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.Supplier;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.SuppliersList;
import uk.gov.crowncommercial.dts.scale.cat.repo.BuyerUserDetailsRepo;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;

/**
 * Message Service layer tests
 */
@SpringBootTest(
    classes = {MessageService.class, JaggaerAPIConfig.class, ModelMapper.class,
        ApplicationFlagsConfig.class, SupplierService.class},
    webEnvironment = WebEnvironment.NONE)
@EnableConfigurationProperties({JaggaerAPIConfig.class})
@ContextConfiguration(classes = {ObjectMapper.class})
@Slf4j
class MessageServiceTest {

  private static final String PRINCIPAL = "venki@bric.org.uk";
  private static final String EVENT_OCID = "ocds-abc123-1";
  private static final Integer PROC_PROJECT_ID = 1;
  private static final String JAGGAER_USER_ID = "12345";

  private static final String RFX_ID = "rfq_0001";

  private static final String JAGGAER_SUPPLIER_NAME_1 = "Doshi Industries";

  static final Integer EXT_ORG_ID = 123455;
  static final Integer EXT_ORG_ID_1 = 123456;
  static final Integer EXT_ORG_ID_2 = 123457;

  static final String SUPPLIER_ORG_ID_1 = "GB-COH-1234567";
  static final String SUPPLIER_ORG_ID = "1234567";

  static final OrganisationMapping ORG_MAPPING = OrganisationMapping.builder().build();
  static final OrganisationMapping ORG_MAPPING_1 = OrganisationMapping.builder().build();
  static final OrganisationMapping ORG_MAPPING_2 = OrganisationMapping.builder().build();

  static final Integer FILE_ID = 1234567;
  static final String FILE_NAME = "filename.dox";
  static final Integer READER_ID = 1234;
  static final String READER_NAME = "John Smith";

  static final ProcurementProject project = ProcurementProject.builder().build();
  public static final String NOT_REPLIED = "Not Replied";
  public static final String DECLINED_TO_RESPOND = "Declined to Respond";

  @MockBean(answer = Answers.RETURNS_DEEP_STUBS)
  private WebClient rpaServiceWebClient;

  @MockBean
  private UserProfileService userProfileService;

  @MockBean
  private JaggaerService jaggaerService;

  @MockBean
  private RetryableTendersDBDelegate retryableTendersDBDelegate;

  @MockBean
  private ConclaveService conclaveService;

  @MockBean
  private ValidationService validationService;

  @MockBean
  private WebclientWrapper webclientWrapper;

  @MockBean
  private AgreementsService agreementsService;

  @Autowired
  private MessageService messageService;

  @MockBean
  private BuyerUserDetailsRepo buyerDetailsRepo;

  @Captor
  ArgumentCaptor<CreateReplyMessage> createReplyMessageArgumentCaptor;

  @BeforeEach
  void beforeEach() {}

  @Test
  void shouldNotDeliverMessageToDeclinedSuppliers() {
    var user = SubUser.builder().userId(JAGGAER_USER_ID).build();
    var event = new ProcurementEvent();
    event.setExternalReferenceId(RFX_ID);
    var rfxResponse = new ExportRfxResponse();
    var list =
        List.of(
            Supplier.builder()
                .companyData(CompanyData.builder().id(1).build())
                .status(NOT_REPLIED)
                .build(),
            Supplier.builder()
                .companyData(CompanyData.builder().id(2).build())
                .status(DECLINED_TO_RESPOND)
                .build());
    rfxResponse.setSuppliersList(SuppliersList.builder().supplier(list).build());
    var message = new Message();
    var ocds = new MessageOCDS();
    message.OCDS(ocds);
    message.nonOCDS(new MessageNonOCDS().isBroadcast(true));

    when(validationService.validateProjectAndEventIds(PROC_PROJECT_ID, EVENT_OCID))
        .thenReturn(event);
    when(userProfileService.resolveBuyerUserProfile(PRINCIPAL)).thenReturn(Optional.of(user));
    when(jaggaerService.getRfxWithSuppliers(event.getExternalEventId())).thenReturn(rfxResponse);
    when(jaggaerService.createReplyMessage(any(CreateReplyMessage.class)))
        .thenReturn(MessageResponse.builder().messageId(1234L).build());

    messageService.createOrReplyMessage(PRINCIPAL, PROC_PROJECT_ID, EVENT_OCID, message);

    verify(jaggaerService).createReplyMessage(createReplyMessageArgumentCaptor.capture());

    var createReplyMessage = createReplyMessageArgumentCaptor.getValue();
    var supplier = createReplyMessage.getSupplierList().getSupplier().get(0);
    assertThat(createReplyMessage.getSupplierList().getSupplier()).hasSize(1);
    assertThat(supplier.getId()).isEqualTo("1");
    assertThat(supplier.getStatus()).isEqualTo(NOT_REPLIED);
    assertThat(createReplyMessage.getBroadcast()).isEqualTo("0");
    assertThat(supplier.getCompanyData().getId()).isEqualTo(1);
  }

  @Test
  void testGetMessages() throws Exception {

    var event = new ProcurementEvent();
    event.setExternalReferenceId(RFX_ID);
    var message = builder().messageId(1).sender(Sender.builder().id(SUPPLIER_ORG_ID).build())
        .category(MessageCategory.builder().categoryName("Technical Clarification").build())
        .sendDate(OffsetDateTime.now()).senderUser(SenderUser.builder().build())
        .subject("Test message").direction(MessageDirection.RECEIVED.getValue())
        .receiverList(ReceiverList.builder()
            .receiver(Arrays.asList(Receiver.builder().id(JAGGAER_USER_ID).build())).build())
        .build();

    var messagesResponse = MessagesResponse.builder()
        .messageList(MessageList.builder().message(Arrays.asList(message)).build()).returnCode(0)
        .returnMessage("").returnedRecords(100).startAt(1).totRecords(120).build();
    var messageRequestInfo =
        MessageRequestInfo.builder().procId(PROC_PROJECT_ID).eventId(EVENT_OCID)
            .messageDirection(MessageDirection.RECEIVED).messageRead(MessageRead.ALL)
            .messageSort(MessageSort.DATE).messageSortOrder(MessageSortOrder.ASCENDING).page(1)
            .pageSize(20).principal(PRINCIPAL).build();
    var user = SubUser.builder().userId(JAGGAER_USER_ID).build();

    // Mock behaviours
    when(userProfileService.resolveBuyerUserProfile(PRINCIPAL)).thenReturn(Optional.of(user));
    when(validationService.validateProjectAndEventIds(PROC_PROJECT_ID, EVENT_OCID))
        .thenReturn(event);
    when(jaggaerService.getMessages(RFX_ID, 1)).thenReturn(messagesResponse);
    when(retryableTendersDBDelegate
        .findOrganisationMappingByExternalOrganisationId(Integer.valueOf(SUPPLIER_ORG_ID)))
            .thenReturn(Optional.of(ORG_MAPPING));

    var response = messageService.getMessagesSummary(messageRequestInfo);

    // Verify
    assertNotNull(response);
    assertEquals(1, response.getMessages().size());
    assertEquals(1, response.getMessages().stream().findFirst().get().getOCDS().getId());
  }

  @Test
  void givenPageSizeAndPageNumberGetMessagesshouldReturnRecordsAccordingly() throws Exception {

    var event = new ProcurementEvent();
    event.setExternalReferenceId(RFX_ID);
    var message = builder().messageId(1).sender(Sender.builder().id(SUPPLIER_ORG_ID).build())
            .category(MessageCategory.builder().categoryName("Technical Clarification").build())
            .sendDate(OffsetDateTime.now()).senderUser(SenderUser.builder().build())
            .subject("Test message").direction(MessageDirection.RECEIVED.getValue())
            .receiverList(ReceiverList.builder()
                    .receiver(Arrays.asList(Receiver.builder().id(JAGGAER_USER_ID).build())).build())
            .build();

    var messagesResponse = MessagesResponse.builder()
            .messageList(MessageList.builder().message(Arrays.asList(message)).build()).returnCode(0)
            .returnMessage("").returnedRecords(100).startAt(1).totRecords(120).build();
    var messageRequestInfo =
            MessageRequestInfo.builder().procId(PROC_PROJECT_ID).eventId(EVENT_OCID)
                    .messageDirection(MessageDirection.RECEIVED).messageRead(MessageRead.ALL)
                    .messageSort(MessageSort.DATE).messageSortOrder(MessageSortOrder.ASCENDING).page(1)
                    .pageSize(20).principal(PRINCIPAL).build();
    var user = SubUser.builder().userId(JAGGAER_USER_ID).build();

    // Mock behaviours
    when(userProfileService.resolveBuyerUserProfile(PRINCIPAL)).thenReturn(Optional.of(user));
    when(validationService.validateProjectAndEventIds(PROC_PROJECT_ID, EVENT_OCID))
            .thenReturn(event);
    when(jaggaerService.getMessages(RFX_ID, 1)).thenReturn(messagesResponse);
    when(retryableTendersDBDelegate
            .findOrganisationMappingByExternalOrganisationId(Integer.valueOf(SUPPLIER_ORG_ID)))
            .thenReturn(Optional.of(ORG_MAPPING));

    var response = messageService.getMessagesSummary(messageRequestInfo);

    // Verify
    assertNotNull(response);
    assertEquals(1, response.getMessages().size());
    assertEquals(1, response.getCounts().getMessagesTotal());
    assertEquals(1, response.getCounts().getPageTotal());
    assertEquals(1, response.getMessages().stream().findFirst().get().getOCDS().getId());
  }
  @Test
  void testGetAttachments() throws Exception {
    // Stub some objects
    var event = new ProcurementEvent();
    event.setExternalReferenceId(RFX_ID);
    var message = builder().messageId(1).sender(Sender.builder().id(SUPPLIER_ORG_ID).build())
        .category(MessageCategory.builder().categoryName("Technical Clarification").build())
        .sendDate(OffsetDateTime.now()).senderUser(SenderUser.builder().build())
        .subject("Test message").direction(MessageDirection.RECEIVED.getValue())
        .attachmentList(AttachmentList.builder()
            .attachment(Arrays
                .asList(Attachment.builder().fileId(FILE_ID + "").fileName(FILE_NAME).build()))
            .build())
        .receiverList(ReceiverList.builder()
            .receiver(Arrays.asList(Receiver.builder().id(JAGGAER_USER_ID).build())).build())
        .build();
    var user = SubUser.builder().userId(JAGGAER_USER_ID).build();

    // Mock behaviours
    when(userProfileService.resolveBuyerUserProfile(PRINCIPAL)).thenReturn(Optional.of(user));
    when(validationService.validateProjectAndEventIds(PROC_PROJECT_ID, EVENT_OCID))
        .thenReturn(event);
    when(jaggaerService.getMessage("1")).thenReturn(message);
    when(jaggaerService.getDocument(FILE_ID, FILE_NAME)).thenReturn(DocumentAttachment.builder()
        .fileName(FILE_NAME).contentType(MediaType.APPLICATION_OCTET_STREAM).build());
    when(retryableTendersDBDelegate
        .findOrganisationMappingByExternalOrganisationId(Integer.valueOf(SUPPLIER_ORG_ID)))
            .thenReturn(Optional.of(ORG_MAPPING));

    var response = messageService.downloadAttachment(PROC_PROJECT_ID, EVENT_OCID, "1", PRINCIPAL,
        FILE_ID + "");

    // Verify
    assertNotNull(response);
    assertEquals(FILE_NAME, response.getFileName());
  }
}
