package uk.gov.crowncommercial.dts.scale.cat.service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;
import static uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.Message.builder;
import java.time.OffsetDateTime;
import java.util.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.web.reactive.function.client.WebClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.config.ApplicationFlagsConfig;
import uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.config.RPAAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.exception.JaggaerRPAException;
import uk.gov.crowncommercial.dts.scale.cat.model.DocumentAttachment;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.BuyerUserDetails;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.OrganisationMapping;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementProject;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.*;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.Message;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.MessageNonOCDS.ClassificationEnum;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.*;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.SubUsers.SubUser;
import uk.gov.crowncommercial.dts.scale.cat.model.rpa.RPAAPIResponse;
import uk.gov.crowncommercial.dts.scale.cat.model.rpa.RPAGenericData;
import uk.gov.crowncommercial.dts.scale.cat.model.rpa.RPAProcessInput;
import uk.gov.crowncommercial.dts.scale.cat.model.rpa.RPAProcessInput.RPAProcessInputBuilder;
import uk.gov.crowncommercial.dts.scale.cat.model.rpa.RPAProcessNameEnum;
import uk.gov.crowncommercial.dts.scale.cat.repo.BuyerUserDetailsRepo;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;

/**
 * Message Service layer tests
 */
@SpringBootTest(
    classes = {MessageService.class, JaggaerAPIConfig.class, ModelMapper.class,
        ApplicationFlagsConfig.class, RPAGenericService.class},
    webEnvironment = WebEnvironment.NONE)
@EnableConfigurationProperties({JaggaerAPIConfig.class, RPAAPIConfig.class})
@ContextConfiguration(classes = {ObjectMapper.class})
@Slf4j
class MessageServiceTest {

  private static final String PRINCIPAL = "venki@bric.org.uk";
  private static final String BUYER_USER_NAME = "Venki Bathula";
  private static final String BUYER_PASSWORD = "PASS12345";
  private static final String EVENT_OCID = "ocds-abc123-1";
  private static final Integer PROC_PROJECT_ID = 1;
  private static final String JAGGAER_USER_ID = "12345";
  private static final String CREATE_MESSAGE = "Create";
  private static final String EXTERNAL_EVENT_ID = "itt_8673";

  private static final String RFX_ID = "rfq_0001";
  private static final Integer JAGGAER_SUPPLIER_ID = 123456;
  private static final String JAGGAER_SUPPLIER_NAME = "Bathula Consulting";

  private static final Integer JAGGAER_SUPPLIER_ID_1 = 123457;
  private static final String JAGGAER_SUPPLIER_NAME_1 = "Doshi Industries";

  static final Integer EXT_ORG_ID = 123455;
  static final Integer EXT_ORG_ID_1 = 123456;
  static final Integer EXT_ORG_ID_2 = 123457;

  static final String SUPPLIER_ORG_ID_1 = "GB-COH-1234567";
  static final String SUPPLIER_ORG_ID = "1234567";
  static final String SUPPLIER_ORG_ID_2 = "GB-COH-7654321";

  static final OrganisationMapping ORG_MAPPING = OrganisationMapping.builder().build();
  static final OrganisationMapping ORG_MAPPING_1 = OrganisationMapping.builder().build();
  static final OrganisationMapping ORG_MAPPING_2 = OrganisationMapping.builder().build();

  static final Integer FILE_ID = 1234567;
  static final String FILE_NAME = "filename.dox";

  static final ProcurementProject project = ProcurementProject.builder().build();

  private static final Optional<SubUser> JAGGAER_USER = Optional
      .of(SubUser.builder().userId(JAGGAER_USER_ID).email(PRINCIPAL).name(BUYER_USER_NAME).build());

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

  @Autowired
  private RPAAPIConfig rpaAPIConfig;

  @MockBean
  private WebclientWrapper webclientWrapper;

  @MockBean
  private AgreementsService agreementsService;

  @Autowired
  private MessageService messageService;

  @MockBean
  private BuyerUserDetailsRepo buyerDetailsRepo;

  private RPAGenericData request = new RPAGenericData();
  private RPAProcessInputBuilder inputBuilder = RPAProcessInput.builder();

  @BeforeEach
  void beforeEach() {
    inputBuilder = RPAProcessInput.builder().userName(PRINCIPAL).password(BUYER_PASSWORD)
        .ittCode("itt_8673").supplierName("").senderName("").messageReceivedDate("");

    request.setProcessName(RPAProcessNameEnum.BUYER_MESSAGING.getValue())
        .setProfileName(rpaAPIConfig.getProfileName()).setSource(rpaAPIConfig.getSource())
        .setRetry(false).setSourceId(rpaAPIConfig.getSourceId()).setRequestTimeout(3600000)
        .setSync(true);

    var jaggerRPACredentials = new HashMap<String, String>();
    jaggerRPACredentials.put("username", rpaAPIConfig.getUserName());
    jaggerRPACredentials.put("password", rpaAPIConfig.getUserPwd());
    var uriTemplate = rpaAPIConfig.getAuthenticationUrl();

    when(webclientWrapper.postData(jaggerRPACredentials, String.class, rpaServiceWebClient,
        rpaAPIConfig.getTimeoutDuration(), uriTemplate)).thenReturn("token");

    when(buyerDetailsRepo.findById(JAGGAER_USER_ID))
        .thenReturn(Optional.of(BuyerUserDetails.builder().userPassword(BUYER_PASSWORD).build()));
  }

  @BeforeAll
  static void beforeClass() {
    ORG_MAPPING.setOrganisationId(SUPPLIER_ORG_ID);
    ORG_MAPPING.setExternalOrganisationId(EXT_ORG_ID);
    ORG_MAPPING_1.setOrganisationId(SUPPLIER_ORG_ID_1);
    ORG_MAPPING_1.setExternalOrganisationId(EXT_ORG_ID_1);
    ORG_MAPPING_2.setOrganisationId(SUPPLIER_ORG_ID_1);
    ORG_MAPPING_2.setExternalOrganisationId(EXT_ORG_ID_2);
    project.setId(PROC_PROJECT_ID);
  }

  @Test
  void testSendMessageToAllSuppliers() throws Exception {
    // Stub some objects
    var ocds = new MessageOCDS().title("Requesting Docs")
        .description("Please send the original documents to appprove request");
    var nonocds = new MessageNonOCDS().isBroadcast(true)
        .classification(ClassificationEnum.TECHNICAL_CLARIFICATION);
    var message = new Message();
    message.setOCDS(ocds);
    message.setNonOCDS(nonocds);
    var responseString = "{\n"
        + "		    \"processInput\": \"{\\\"Search.Username\\\":\\\"venki.bathula@brickendon.com\\\",\\\"Search.Password\\\":\\\"password\\\",\\\"Search.ITTCode\\\":\\\"itt_8673\\\",\\\"Search.BroadcastMessage\\\":\\\"Yes\\\",\\\"Search.MessagingAction\\\":\\\"Create\\\",\\\"Search.MessageSubject\\\":\\\"SIT Test\\\",\\\"Search.MessageBody\\\":\\\"Yes! we need a company registration document.\\\",\\\"Search.MessageClassification\\\":\\\"Technical Clarification\\\",\\\"Search.SupplierName\\\":\\\"\\\",\\\"Search.SenderName\\\":\\\"Venki Bathula\\\",\\\"Search.MessageReceivedDate\\\":\\\"\\\"}\",\n"
        + "		    \"processName\": \"BuyerMessaging\",\n"
        + "		    \"profileName\": \"ITTEvaluation\",\n" + "		    \"source\": \"Postman\",\n"
        + "		    \"sourceId\": \"1\",\n" + "		    \"retry\": false,\n"
        + "		    \"retryConfigurations\": {\n" + "		        \"previousRequestIds\": [],\n"
        + "		        \"noOfTimesRetry\": 0,\n"
        + "		        \"retryCoolOffInterval\": 10000\n" + "		    },\n"
        + "		    \"requestTimeout\": 3600000,\n" + "		    \"isSync\": true,\n"
        + "		    \"transactionId\": \"ab85bb4f-0204-4e3b-a2fa-f69cbb0e4134\",\n"
        + "		    \"status\": \"success\",\n" + "		    \"response\": {\n"
        + "		        \"response\": \"{\\\"AutomationOutputData\\\":[{\\\"AppName\\\":\\\"CCS\\\",\\\"CviewDictionary\\\":{}},{\\\"AppName\\\":\\\"Microbot : API_ResponsePayload\\\",\\\"CviewDictionary\\\":{\\\"ErrorDescription\\\":\\\"\\\",\\\"IsError\\\":\\\"False\\\",\\\"Status\\\":\\\"Create message for itt_8673 completed by venki.bathula@brickendon.com\\\"}}],\\\"HttpStatus\\\":\\\"\\\",\\\"ParentRequest\\\":{\\\"AutomationInputDictionary\\\":{\\\"Search.Username\\\":\\\"venki.bathula@brickendon.com\\\",\\\"Search.Password\\\":\\\"password\\\",\\\"Search.ITTCode\\\":\\\"itt_8673\\\",\\\"Search.BroadcastMessage\\\":\\\"Yes\\\",\\\"Search.MessagingAction\\\":\\\"Create\\\",\\\"Search.MessageSubject\\\":\\\"SIT Test\\\",\\\"Search.MessageBody\\\":\\\"Yes! we need a company registration document.\\\",\\\"Search.MessageClassification\\\":\\\"Technical Clarification\\\",\\\"Search.SupplierName\\\":\\\"\\\",\\\"Search.SenderName\\\":\\\"Venki Bathula\\\",\\\"Search.MessageReceivedDate\\\":\\\"\\\"},\\\"MaskedAutomationInputDictionary\\\":{\\\"Search.Username\\\":\\\"venki.bathula@brickendon.com\\\",\\\"Search.Password\\\":\\\"HPSW0Mx6ZRMXo/ok+T/2u/+BY5gjR1ajS+LgqtyPSRYz/kXgKywqh4AeYduOpypcshDH82OEF6osmnj0b3gNYg==\\\",\\\"Search.ITTCode\\\":\\\"itt_8673\\\",\\\"Search.BroadcastMessage\\\":\\\"Yes\\\",\\\"Search.MessagingAction\\\":\\\"Create\\\",\\\"Search.MessageSubject\\\":\\\"SIT Test\\\",\\\"Search.MessageBody\\\":\\\"Yes! we need a company registration document.\\\",\\\"Search.MessageClassification\\\":\\\"Technical Clarification\\\",\\\"Search.SupplierName\\\":\\\"\\\",\\\"Search.SenderName\\\":\\\"Venki Bathula\\\",\\\"Search.MessageReceivedDate\\\":\\\"\\\"},\\\"AnonymizedAutomationInputDictionary\\\":{\\\"Search.Username\\\":\\\"venki.bathula@brickendon.com\\\",\\\"Search.Password\\\":\\\"HPSW0Mx6ZRMXo/ok+T/2u/+BY5gjR1ajS+LgqtyPSRYz/kXgKywqh4AeYduOpypcshDH82OEF6osmnj0b3gNYg==\\\",\\\"Search.ITTCode\\\":\\\"itt_8673\\\",\\\"Search.BroadcastMessage\\\":\\\"Yes\\\",\\\"Search.MessagingAction\\\":\\\"Create\\\",\\\"Search.MessageSubject\\\":\\\"SIT Test\\\",\\\"Search.MessageBody\\\":\\\"Yes! we need a company registration document.\\\",\\\"Search.MessageClassification\\\":\\\"Technical Clarification\\\",\\\"Search.SupplierName\\\":\\\"\\\",\\\"Search.SenderName\\\":\\\"Venki Bathula\\\",\\\"Search.MessageReceivedDate\\\":\\\"\\\"},\\\"DataProtectionErrors\\\":{},\\\"ProcessName\\\":\\\"BuyerMessaging\\\",\\\"ProfileName\\\":\\\"ITTEvaluation\\\",\\\"RetryFlag\\\":false,\\\"Retry\\\":{\\\"NoOfTimesRetry\\\":0,\\\"RetryCoolOffInterval\\\":10000,\\\"PreviousRequestIds\\\":[]},\\\"APIVersion\\\":\\\"\\\",\\\"AppId\\\":\\\"\\\",\\\"CommandExecutionWindow\\\":\\\"\\\",\\\"CommandGenerationSource\\\":\\\"\\\",\\\"Country\\\":\\\"\\\",\\\"Instance\\\":\\\"\\\",\\\"PartnerId\\\":\\\"\\\",\\\"ReferenceCode\\\":\\\"\\\",\\\"Timestamp\\\":\\\"2022-02-15T22:21:46.190Z\\\",\\\"UserName\\\":\\\"testuser\\\",\\\"VID\\\":null,\\\"SENodeId\\\":null,\\\"RequestExpiration\\\":\\\"3600000\\\",\\\"wait_for_completion\\\":\\\"true\\\",\\\"Impersonation\\\":null,\\\"IfExternalVaultType\\\":false},\\\"ReferenceCode\\\":\\\"\\\",\\\"RequestExecutionTime\\\":\\\"00:00:14.6458043\\\",\\\"SEReferenceCode\\\":\\\"\\\",\\\"SpareParam1\\\":\\\"\\\",\\\"SpareParam2\\\":\\\"\\\",\\\"UserName\\\":\\\"testuser\\\",\\\"CommandResultEnum\\\":0,\\\"CommandResultDescription\\\":\\\"SearchSuccessful\\\",\\\"ErrorDetailObject\\\":[],\\\"SENodeId\\\":null,\\\"RequestReceivedTime\\\":\\\"2022-02-15T22:21:46.2043795Z\\\",\\\"RequestProcessedTime\\\":\\\"2022-02-15T22:22:00.8501838Z\\\",\\\"TransactionDescription\\\":\\\"SearchSuccessful\\\"}\"\n"
        + "		    },\n" + "		    \"error\": {}\n" + "		}\"";

    inputBuilder.broadcastMessage(nonocds.getIsBroadcast() ? "Yes" : "No")
        .messagingAction(CREATE_MESSAGE).messageSubject(ocds.getTitle())
        .messageBody(ocds.getDescription())
        .messageClassification(nonocds.getClassification().getValue());

    request.setProcessInput(new ObjectMapper().writeValueAsString(inputBuilder.build()));

    RPAAPIResponse responseObject =
        new ObjectMapper().readValue(responseString, RPAAPIResponse.class);

    log.info("Test Request: {}", new ObjectMapper().writeValueAsString(request));

    when(webclientWrapper.postDataWithToken(request, RPAAPIResponse.class, rpaServiceWebClient,
        rpaAPIConfig.getTimeoutDuration(), rpaAPIConfig.getAccessUrl(), "token"))
            .thenReturn(responseObject);

    // Mock behaviours
    when(userProfileService.resolveBuyerUserProfile(PRINCIPAL)).thenReturn(JAGGAER_USER);
    when(validationService.validateProjectAndEventIds(PROC_PROJECT_ID, EVENT_OCID))
        .thenReturn(ProcurementEvent.builder().externalReferenceId(EXTERNAL_EVENT_ID).build());

    // Invoke
    var messageResponse =
        messageService.sendOrRespondMessage(PRINCIPAL, PROC_PROJECT_ID, EVENT_OCID, message);

    // Assert
    assertAll(() -> assertNotNull(messageResponse),
        () -> assertEquals("Create message for itt_8673 completed by venki.bathula@brickendon.com",
            messageResponse));
  }

  @Test
  void testSendMessageToInvalidSupplierInList() throws Exception {
    // Stub some objects
    var ocds = new MessageOCDS().title("Requesting Docs")
        .description("Please send the original documents to appprove request");
    var nonocds = new MessageNonOCDS().isBroadcast(false)
        .classification(ClassificationEnum.TECHNICAL_CLARIFICATION);
    List<OrganizationReference1> receiverList = new ArrayList<OrganizationReference1>();
    receiverList.add(new OrganizationReference1().id("GB-COH-1234567"));
    receiverList.add(new OrganizationReference1().id("GB-COH-7654320"));
    nonocds.setReceiverList(receiverList);

    var message = new Message();
    message.setOCDS(ocds);
    message.setNonOCDS(nonocds);
    var rfxResponse = prepareSupplierDetails();
    var responseString = "{\n"
        + "    \"processInput\": \"{\\\"Search.Username\\\":\\\"venki.bathula@brickendon.com\\\",\\\"Search.Password\\\":\\\"password\\\",\\\"Search.ITTCode\\\":\\\"itt_8673\\\",\\\"Search.BroadcastMessage\\\":\\\"No\\\",\\\"Search.MessagingAction\\\":\\\"Create\\\",\\\"Search.MessageSubject\\\":\\\"SIT Test\\\",\\\"Search.MessageBody\\\":\\\"Yes! we need a company registration document.\\\",\\\"Search.MessageClassification\\\":\\\"Technical Clarification\\\",\\\"Search.SupplierName\\\":\\\"Bathula Consulting\\\",\\\"Search.SenderName\\\":\\\"Venki Bathula\\\",\\\"Search.MessageReceivedDate\\\":\\\"\\\"}\",\n"
        + "    \"processName\": \"BuyerMessaging\",\n" + "    \"profileName\": \"ITTEvaluation\",\n"
        + "    \"source\": \"Postman\",\n" + "    \"sourceId\": \"1\",\n"
        + "    \"retry\": false,\n" + "    \"retryConfigurations\": {\n"
        + "        \"previousRequestIds\": [],\n" + "        \"noOfTimesRetry\": 0,\n"
        + "        \"retryCoolOffInterval\": 10000\n" + "    },\n"
        + "    \"requestTimeout\": 3600000,\n" + "    \"isSync\": true,\n"
        + "    \"transactionId\": \"cafcaae1-f627-405d-a067-0313fc4b380c\",\n"
        + "    \"status\": \"success\",\n" + "    \"response\": {\n"
        + "        \"response\": \"{\\\"AutomationOutputData\\\":[{\\\"AppName\\\":\\\"CCS\\\",\\\"CviewDictionary\\\":{}},{\\\"AppName\\\":\\\"Microbot : API_ResponsePayload\\\",\\\"CviewDictionary\\\":{\\\"ErrorDescription\\\":\\\"\\\",\\\"IsError\\\":\\\"False\\\",\\\"Status\\\":\\\"Create message for itt_8673 completed by venki.bathula@brickendon.com\\\"}}],\\\"HttpStatus\\\":\\\"\\\",\\\"ParentRequest\\\":{\\\"AutomationInputDictionary\\\":{\\\"Search.Username\\\":\\\"venki.bathula@brickendon.com\\\",\\\"Search.Password\\\":\\\"password\\\",\\\"Search.ITTCode\\\":\\\"itt_8673\\\",\\\"Search.BroadcastMessage\\\":\\\"No\\\",\\\"Search.MessagingAction\\\":\\\"Create\\\",\\\"Search.MessageSubject\\\":\\\"SIT Test\\\",\\\"Search.MessageBody\\\":\\\"Yes! we need a company registration document.\\\",\\\"Search.MessageClassification\\\":\\\"Technical Clarification\\\",\\\"Search.SupplierName\\\":\\\"Bathula Consulting\\\",\\\"Search.SenderName\\\":\\\"Venki Bathula\\\",\\\"Search.MessageReceivedDate\\\":\\\"\\\"},\\\"MaskedAutomationInputDictionary\\\":{\\\"Search.Username\\\":\\\"venki.bathula@brickendon.com\\\",\\\"Search.Password\\\":\\\"HPSW0Mx6ZRMXo/ok+T/2u/+BY5gjR1ajS+LgqtyPSRYz/kXgKywqh4AeYduOpypcshDH82OEF6osmnj0b3gNYg==\\\",\\\"Search.ITTCode\\\":\\\"itt_8673\\\",\\\"Search.BroadcastMessage\\\":\\\"No\\\",\\\"Search.MessagingAction\\\":\\\"Create\\\",\\\"Search.MessageSubject\\\":\\\"SIT Test\\\",\\\"Search.MessageBody\\\":\\\"Yes! we need a company registration document.\\\",\\\"Search.MessageClassification\\\":\\\"Technical Clarification\\\",\\\"Search.SupplierName\\\":\\\"Bathula Consulting\\\",\\\"Search.SenderName\\\":\\\"Venki Bathula\\\",\\\"Search.MessageReceivedDate\\\":\\\"\\\"},\\\"AnonymizedAutomationInputDictionary\\\":{\\\"Search.Username\\\":\\\"venki.bathula@brickendon.com\\\",\\\"Search.Password\\\":\\\"HPSW0Mx6ZRMXo/ok+T/2u/+BY5gjR1ajS+LgqtyPSRYz/kXgKywqh4AeYduOpypcshDH82OEF6osmnj0b3gNYg==\\\",\\\"Search.ITTCode\\\":\\\"itt_8673\\\",\\\"Search.BroadcastMessage\\\":\\\"No\\\",\\\"Search.MessagingAction\\\":\\\"Create\\\",\\\"Search.MessageSubject\\\":\\\"SIT Test\\\",\\\"Search.MessageBody\\\":\\\"Yes! we need a company registration document.\\\",\\\"Search.MessageClassification\\\":\\\"Technical Clarification\\\",\\\"Search.SupplierName\\\":\\\"Bathula Consulting\\\",\\\"Search.SenderName\\\":\\\"Venki Bathula\\\",\\\"Search.MessageReceivedDate\\\":\\\"\\\"},\\\"DataProtectionErrors\\\":{},\\\"ProcessName\\\":\\\"BuyerMessaging\\\",\\\"ProfileName\\\":\\\"ITTEvaluation\\\",\\\"RetryFlag\\\":false,\\\"Retry\\\":{\\\"NoOfTimesRetry\\\":0,\\\"RetryCoolOffInterval\\\":10000,\\\"PreviousRequestIds\\\":[]},\\\"APIVersion\\\":\\\"\\\",\\\"AppId\\\":\\\"\\\",\\\"CommandExecutionWindow\\\":\\\"\\\",\\\"CommandGenerationSource\\\":\\\"\\\",\\\"Country\\\":\\\"\\\",\\\"Instance\\\":\\\"\\\",\\\"PartnerId\\\":\\\"\\\",\\\"ReferenceCode\\\":\\\"\\\",\\\"Timestamp\\\":\\\"2022-02-16T13:26:30.159Z\\\",\\\"UserName\\\":\\\"testuser\\\",\\\"VID\\\":null,\\\"SENodeId\\\":null,\\\"RequestExpiration\\\":\\\"3600000\\\",\\\"wait_for_completion\\\":\\\"true\\\",\\\"Impersonation\\\":null,\\\"IfExternalVaultType\\\":false},\\\"ReferenceCode\\\":\\\"\\\",\\\"RequestExecutionTime\\\":\\\"00:00:08.3225069\\\",\\\"SEReferenceCode\\\":\\\"\\\",\\\"SpareParam1\\\":\\\"\\\",\\\"SpareParam2\\\":\\\"\\\",\\\"UserName\\\":\\\"testuser\\\",\\\"CommandResultEnum\\\":0,\\\"CommandResultDescription\\\":\\\"SearchSuccessful\\\",\\\"ErrorDetailObject\\\":[],\\\"SENodeId\\\":null,\\\"RequestReceivedTime\\\":\\\"2022-02-16T13:26:30.1801763Z\\\",\\\"RequestProcessedTime\\\":\\\"2022-02-16T13:26:38.5026832Z\\\",\\\"TransactionDescription\\\":\\\"SearchSuccessful\\\"}\"\n"
        + "    },\n" + "    \"error\": {}\n" + "}";

    request.setProcessInput(new ObjectMapper().writeValueAsString(inputBuilder.build()));

    log.info("Test Request SendMessageToInvalidSupplierInList: {}",
        new ObjectMapper().writeValueAsString(request));

    RPAAPIResponse responseObject =
        new ObjectMapper().readValue(responseString, RPAAPIResponse.class);

    // Mock behaviours
    when(webclientWrapper.postDataWithToken(request, RPAAPIResponse.class, rpaServiceWebClient,
        rpaAPIConfig.getTimeoutDuration(), rpaAPIConfig.getAccessUrl(), "token"))
            .thenReturn(responseObject);

    when(jaggaerService.getRfx(RFX_ID)).thenReturn(rfxResponse);
    when(retryableTendersDBDelegate
        .findOrganisationMappingByOrganisationIdIn(Set.of(SUPPLIER_ORG_ID_1, SUPPLIER_ORG_ID_2)))
            .thenReturn(Set.of(ORG_MAPPING_1, ORG_MAPPING_2));
    when(userProfileService.resolveBuyerUserProfile(PRINCIPAL)).thenReturn(JAGGAER_USER);
    when(validationService.validateProjectAndEventIds(PROC_PROJECT_ID, EVENT_OCID)).thenReturn(
        ProcurementEvent.builder().externalReferenceId(EXTERNAL_EVENT_ID).externalEventId(RFX_ID)
            .project(project).ocdsAuthorityName("ocds").ocidPrefix("abc123").id(1).build());

    // Invoke
    var thrown = Assertions.assertThrows(JaggaerRPAException.class, () -> {
      messageService.sendOrRespondMessage(PRINCIPAL, PROC_PROJECT_ID, EVENT_OCID, message);
    });

    // Assert
    Assertions.assertEquals(
        "Jaggaer RPA application exception, Code: [N/A], Message: [No supplier organisation mappings found in Tenders DB [GB-COH-1234567, GB-COH-7654320]]",
        thrown.getMessage());
  }

  @Test
  void testSendMessageToSelectedSuppliers() throws Exception {
    // Stub some objects
    var ocds = new MessageOCDS().title("Requesting Docs")
        .description("Please send the original documents to appprove request");
    var nonocds = new MessageNonOCDS().isBroadcast(false)
        .classification(ClassificationEnum.TECHNICAL_CLARIFICATION);
    var receiverList = new ArrayList<OrganizationReference1>();
    receiverList.add(new OrganizationReference1().id("GB-COH-1234567"));
    receiverList.add(new OrganizationReference1().id("GB-COH-7654321"));
    nonocds.setReceiverList(receiverList);
    var message = new Message();
    message.setOCDS(ocds);
    message.setNonOCDS(nonocds);
    var rfxResponse = prepareSupplierDetails();
    var responseString = "{\n"
        + "    \"processInput\": \"{\\\"Search.Username\\\":\\\"venki.bathula@brickendon.com\\\",\\\"Search.Password\\\":\\\"password\\\",\\\"Search.ITTCode\\\":\\\"itt_8673\\\",\\\"Search.BroadcastMessage\\\":\\\"No\\\",\\\"Search.MessagingAction\\\":\\\"Create\\\",\\\"Search.MessageSubject\\\":\\\"SIT Test\\\",\\\"Search.MessageBody\\\":\\\"Yes! we need a company registration document.\\\",\\\"Search.MessageClassification\\\":\\\"Technical Clarification\\\",\\\"Search.SupplierName\\\":\\\"Bathula Consulting\\\",\\\"Search.SenderName\\\":\\\"Venki Bathula\\\",\\\"Search.MessageReceivedDate\\\":\\\"\\\"}\",\n"
        + "    \"processName\": \"BuyerMessaging\",\n" + "    \"profileName\": \"ITTEvaluation\",\n"
        + "    \"source\": \"Postman\",\n" + "    \"sourceId\": \"1\",\n"
        + "    \"retry\": false,\n" + "    \"retryConfigurations\": {\n"
        + "        \"previousRequestIds\": [],\n" + "        \"noOfTimesRetry\": 0,\n"
        + "        \"retryCoolOffInterval\": 10000\n" + "    },\n"
        + "    \"requestTimeout\": 3600000,\n" + "    \"isSync\": true,\n"
        + "    \"transactionId\": \"cafcaae1-f627-405d-a067-0313fc4b380c\",\n"
        + "    \"status\": \"success\",\n" + "    \"response\": {\n"
        + "        \"response\": \"{\\\"AutomationOutputData\\\":[{\\\"AppName\\\":\\\"CCS\\\",\\\"CviewDictionary\\\":{}},{\\\"AppName\\\":\\\"Microbot : API_ResponsePayload\\\",\\\"CviewDictionary\\\":{\\\"ErrorDescription\\\":\\\"\\\",\\\"IsError\\\":\\\"False\\\",\\\"Status\\\":\\\"Create message for itt_8673 completed by venki.bathula@brickendon.com\\\"}}],\\\"HttpStatus\\\":\\\"\\\",\\\"ParentRequest\\\":{\\\"AutomationInputDictionary\\\":{\\\"Search.Username\\\":\\\"venki.bathula@brickendon.com\\\",\\\"Search.Password\\\":\\\"password\\\",\\\"Search.ITTCode\\\":\\\"itt_8673\\\",\\\"Search.BroadcastMessage\\\":\\\"No\\\",\\\"Search.MessagingAction\\\":\\\"Create\\\",\\\"Search.MessageSubject\\\":\\\"SIT Test\\\",\\\"Search.MessageBody\\\":\\\"Yes! we need a company registration document.\\\",\\\"Search.MessageClassification\\\":\\\"Technical Clarification\\\",\\\"Search.SupplierName\\\":\\\"Bathula Consulting\\\",\\\"Search.SenderName\\\":\\\"Venki Bathula\\\",\\\"Search.MessageReceivedDate\\\":\\\"\\\"},\\\"MaskedAutomationInputDictionary\\\":{\\\"Search.Username\\\":\\\"venki.bathula@brickendon.com\\\",\\\"Search.Password\\\":\\\"HPSW0Mx6ZRMXo/ok+T/2u/+BY5gjR1ajS+LgqtyPSRYz/kXgKywqh4AeYduOpypcshDH82OEF6osmnj0b3gNYg==\\\",\\\"Search.ITTCode\\\":\\\"itt_8673\\\",\\\"Search.BroadcastMessage\\\":\\\"No\\\",\\\"Search.MessagingAction\\\":\\\"Create\\\",\\\"Search.MessageSubject\\\":\\\"SIT Test\\\",\\\"Search.MessageBody\\\":\\\"Yes! we need a company registration document.\\\",\\\"Search.MessageClassification\\\":\\\"Technical Clarification\\\",\\\"Search.SupplierName\\\":\\\"Bathula Consulting\\\",\\\"Search.SenderName\\\":\\\"Venki Bathula\\\",\\\"Search.MessageReceivedDate\\\":\\\"\\\"},\\\"AnonymizedAutomationInputDictionary\\\":{\\\"Search.Username\\\":\\\"venki.bathula@brickendon.com\\\",\\\"Search.Password\\\":\\\"HPSW0Mx6ZRMXo/ok+T/2u/+BY5gjR1ajS+LgqtyPSRYz/kXgKywqh4AeYduOpypcshDH82OEF6osmnj0b3gNYg==\\\",\\\"Search.ITTCode\\\":\\\"itt_8673\\\",\\\"Search.BroadcastMessage\\\":\\\"No\\\",\\\"Search.MessagingAction\\\":\\\"Create\\\",\\\"Search.MessageSubject\\\":\\\"SIT Test\\\",\\\"Search.MessageBody\\\":\\\"Yes! we need a company registration document.\\\",\\\"Search.MessageClassification\\\":\\\"Technical Clarification\\\",\\\"Search.SupplierName\\\":\\\"Bathula Consulting\\\",\\\"Search.SenderName\\\":\\\"Venki Bathula\\\",\\\"Search.MessageReceivedDate\\\":\\\"\\\"},\\\"DataProtectionErrors\\\":{},\\\"ProcessName\\\":\\\"BuyerMessaging\\\",\\\"ProfileName\\\":\\\"ITTEvaluation\\\",\\\"RetryFlag\\\":false,\\\"Retry\\\":{\\\"NoOfTimesRetry\\\":0,\\\"RetryCoolOffInterval\\\":10000,\\\"PreviousRequestIds\\\":[]},\\\"APIVersion\\\":\\\"\\\",\\\"AppId\\\":\\\"\\\",\\\"CommandExecutionWindow\\\":\\\"\\\",\\\"CommandGenerationSource\\\":\\\"\\\",\\\"Country\\\":\\\"\\\",\\\"Instance\\\":\\\"\\\",\\\"PartnerId\\\":\\\"\\\",\\\"ReferenceCode\\\":\\\"\\\",\\\"Timestamp\\\":\\\"2022-02-16T13:26:30.159Z\\\",\\\"UserName\\\":\\\"testuser\\\",\\\"VID\\\":null,\\\"SENodeId\\\":null,\\\"RequestExpiration\\\":\\\"3600000\\\",\\\"wait_for_completion\\\":\\\"true\\\",\\\"Impersonation\\\":null,\\\"IfExternalVaultType\\\":false},\\\"ReferenceCode\\\":\\\"\\\",\\\"RequestExecutionTime\\\":\\\"00:00:08.3225069\\\",\\\"SEReferenceCode\\\":\\\"\\\",\\\"SpareParam1\\\":\\\"\\\",\\\"SpareParam2\\\":\\\"\\\",\\\"UserName\\\":\\\"testuser\\\",\\\"CommandResultEnum\\\":0,\\\"CommandResultDescription\\\":\\\"SearchSuccessful\\\",\\\"ErrorDetailObject\\\":[],\\\"SENodeId\\\":null,\\\"RequestReceivedTime\\\":\\\"2022-02-16T13:26:30.1801763Z\\\",\\\"RequestProcessedTime\\\":\\\"2022-02-16T13:26:38.5026832Z\\\",\\\"TransactionDescription\\\":\\\"SearchSuccessful\\\"}\"\n"
        + "    },\n" + "    \"error\": {}\n" + "}";

    inputBuilder.broadcastMessage(nonocds.getIsBroadcast() ? "Yes" : "No")
        .messagingAction(CREATE_MESSAGE).messageSubject(ocds.getTitle())
        .messageBody(ocds.getDescription())
        .messageClassification(nonocds.getClassification().getValue())
        .supplierName("Bathula Consulting~|Doshi Industries");

    request.setProcessInput(new ObjectMapper().writeValueAsString(inputBuilder.build()));

    log.info("Test Request SendMessageToSelectedSuppliers: {}",
        new ObjectMapper().writeValueAsString(request));

    RPAAPIResponse responseObject =
        new ObjectMapper().readValue(responseString, RPAAPIResponse.class);

    // Mock behaviours
    when(webclientWrapper.postDataWithToken(request, RPAAPIResponse.class, rpaServiceWebClient,
        rpaAPIConfig.getTimeoutDuration(), rpaAPIConfig.getAccessUrl(), "token"))
            .thenReturn(responseObject);

    when(jaggaerService.getRfx(RFX_ID)).thenReturn(rfxResponse);
    when(retryableTendersDBDelegate
        .findOrganisationMappingByOrganisationIdIn(Set.of(SUPPLIER_ORG_ID_1, SUPPLIER_ORG_ID_2)))
            .thenReturn(Set.of(ORG_MAPPING_1, ORG_MAPPING_2));
    when(userProfileService.resolveBuyerUserProfile(PRINCIPAL)).thenReturn(JAGGAER_USER);
    when(validationService.validateProjectAndEventIds(PROC_PROJECT_ID, EVENT_OCID))
        .thenReturn(ProcurementEvent.builder().externalReferenceId(EXTERNAL_EVENT_ID)
            .externalEventId(RFX_ID).build());

    // Invoke
    var messageResponse =
        messageService.sendOrRespondMessage(PRINCIPAL, PROC_PROJECT_ID, EVENT_OCID, message);

    // Assert
    assertAll(() -> assertNotNull(messageResponse),
        () -> assertEquals("Create message for itt_8673 completed by venki.bathula@brickendon.com",
            messageResponse));
  }

  @Test
  void testMapper() throws JsonMappingException, JsonProcessingException {
    String resp = "{\n"
        + "    \"processInput\": \"{\\\"Search.Username\\\":\\\"venki.bathula@brickendon.com\\\",\\\"Search.Password\\\":\\\"pwd\\\",\\\"Search.ITTCode\\\":\\\"itt_86\\\",\\\"Search.BroadcastMessage\\\":\\\"Yes\\\",\\\"Search.MessagingAction\\\":\\\"Create\\\",\\\"Search.MessageSubject\\\":\\\"SIT Test\\\",\\\"Search.MessageBody\\\":\\\"Yes! we need a company registration document.\\\",\\\"Search.MessageClassification\\\":\\\"Technical Clarification\\\",\\\"Search.SupplierName\\\":\\\"\\\",\\\"Search.SenderName\\\":\\\"Venki Bathula\\\",\\\"Search.MessageReceivedDate\\\":\\\"\\\"}\",\n"
        + "    \"processName\": \"BuyerMessaging\",\n" + "    \"profileName\": \"ITTEvaluation\",\n"
        + "    \"source\": \"Postman\",\n" + "    \"sourceId\": \"1\",\n"
        + "    \"retry\": false,\n" + "    \"retryConfigurations\": {\n"
        + "        \"previousRequestIds\": [],\n" + "        \"noOfTimesRetry\": 0,\n"
        + "        \"retryCoolOffInterval\": 10000\n" + "    },\n"
        + "    \"requestTimeout\": 3600000,\n" + "    \"isSync\": true,\n"
        + "    \"transactionId\": \"bc33b354-67ea-496c-8e84-f2dcf6dd6184\",\n"
        + "    \"status\": \"success\",\n" + "    \"response\": {\n"
        + "        \"response\": \"{\\\"AutomationOutputData\\\":[{\\\"AppName\\\":\\\"CCS\\\",\\\"CviewDictionary\\\":{}},{\\\"AppName\\\":\\\"Microbot : API_ResponsePayload\\\",\\\"CviewDictionary\\\":{\\\"ErrorDescription\\\":\\\"ITT Code 'itt_86' not found.\\\",\\\"IsError\\\":\\\"True\\\",\\\"Status\\\":\\\"Create message for itt_86 failed.\\\"}}],\\\"HttpStatus\\\":\\\"\\\",\\\"ParentRequest\\\":{\\\"AutomationInputDictionary\\\":{\\\"Search.Username\\\":\\\"venki.bathula@brickendon.com\\\",\\\"Search.Password\\\":\\\"pwd\\\",\\\"Search.ITTCode\\\":\\\"itt_86\\\",\\\"Search.BroadcastMessage\\\":\\\"Yes\\\",\\\"Search.MessagingAction\\\":\\\"Create\\\",\\\"Search.MessageSubject\\\":\\\"SIT Test\\\",\\\"Search.MessageBody\\\":\\\"Yes! we need a company registration document.\\\",\\\"Search.MessageClassification\\\":\\\"Technical Clarification\\\",\\\"Search.SupplierName\\\":\\\"\\\",\\\"Search.SenderName\\\":\\\"Venki Bathula\\\",\\\"Search.MessageReceivedDate\\\":\\\"\\\"},\\\"MaskedAutomationInputDictionary\\\":{\\\"Search.Username\\\":\\\"venki.bathula@brickendon.com\\\",\\\"Search.Password\\\":\\\"HPSW0Mx6ZRMXo/ok+T/2u/+BY5gjR1ajS+LgqtyPSRYz/kXgKywqh4AeYduOpypcshDH82OEF6osmnj0b3gNYg==\\\",\\\"Search.ITTCode\\\":\\\"itt_86\\\",\\\"Search.BroadcastMessage\\\":\\\"Yes\\\",\\\"Search.MessagingAction\\\":\\\"Create\\\",\\\"Search.MessageSubject\\\":\\\"SIT Test\\\",\\\"Search.MessageBody\\\":\\\"Yes! we need a company registration document.\\\",\\\"Search.MessageClassification\\\":\\\"Technical Clarification\\\",\\\"Search.SupplierName\\\":\\\"\\\",\\\"Search.SenderName\\\":\\\"Venki Bathula\\\",\\\"Search.MessageReceivedDate\\\":\\\"\\\"},\\\"AnonymizedAutomationInputDictionary\\\":{\\\"Search.Username\\\":\\\"venki.bathula@brickendon.com\\\",\\\"Search.Password\\\":\\\"HPSW0Mx6ZRMXo/ok+T/2u/+BY5gjR1ajS+LgqtyPSRYz/kXgKywqh4AeYduOpypcshDH82OEF6osmnj0b3gNYg==\\\",\\\"Search.ITTCode\\\":\\\"itt_86\\\",\\\"Search.BroadcastMessage\\\":\\\"Yes\\\",\\\"Search.MessagingAction\\\":\\\"Create\\\",\\\"Search.MessageSubject\\\":\\\"SIT Test\\\",\\\"Search.MessageBody\\\":\\\"Yes! we need a company registration document.\\\",\\\"Search.MessageClassification\\\":\\\"Technical Clarification\\\",\\\"Search.SupplierName\\\":\\\"\\\",\\\"Search.SenderName\\\":\\\"Venki Bathula\\\",\\\"Search.MessageReceivedDate\\\":\\\"\\\"},\\\"DataProtectionErrors\\\":{},\\\"ProcessName\\\":\\\"BuyerMessaging\\\",\\\"ProfileName\\\":\\\"ITTEvaluation\\\",\\\"RetryFlag\\\":false,\\\"Retry\\\":{\\\"NoOfTimesRetry\\\":0,\\\"RetryCoolOffInterval\\\":10000,\\\"PreviousRequestIds\\\":[]},\\\"APIVersion\\\":\\\"\\\",\\\"AppId\\\":\\\"\\\",\\\"CommandExecutionWindow\\\":\\\"\\\",\\\"CommandGenerationSource\\\":\\\"\\\",\\\"Country\\\":\\\"\\\",\\\"Instance\\\":\\\"\\\",\\\"PartnerId\\\":\\\"\\\",\\\"ReferenceCode\\\":\\\"\\\",\\\"Timestamp\\\":\\\"2022-02-16T13:34:04.995Z\\\",\\\"UserName\\\":\\\"testuser\\\",\\\"VID\\\":null,\\\"SENodeId\\\":null,\\\"RequestExpiration\\\":\\\"3600000\\\",\\\"wait_for_completion\\\":\\\"true\\\",\\\"Impersonation\\\":null,\\\"IfExternalVaultType\\\":false},\\\"ReferenceCode\\\":\\\"\\\",\\\"RequestExecutionTime\\\":\\\"00:00:06.1918055\\\",\\\"SEReferenceCode\\\":\\\"\\\",\\\"SpareParam1\\\":\\\"\\\",\\\"SpareParam2\\\":\\\"\\\",\\\"UserName\\\":\\\"testuser\\\",\\\"CommandResultEnum\\\":0,\\\"CommandResultDescription\\\":\\\"SearchSuccessful\\\",\\\"ErrorDetailObject\\\":[],\\\"SENodeId\\\":null,\\\"RequestReceivedTime\\\":\\\"2022-02-16T13:34:05.0083363Z\\\",\\\"RequestProcessedTime\\\":\\\"2022-02-16T13:34:11.2001418Z\\\",\\\"TransactionDescription\\\":\\\"SearchSuccessful\\\"}\"\n"
        + "    },\n" + "    \"error\": {}\n" + "}";

    RPAAPIResponse readValue = new ObjectMapper().readValue(resp, RPAAPIResponse.class);
    assertNotNull(readValue);
  }

  private ExportRfxResponse prepareSupplierDetails() {
    var companyData =
        CompanyData.builder().id(JAGGAER_SUPPLIER_ID).name(JAGGAER_SUPPLIER_NAME).build();
    var companyData1 =
        CompanyData.builder().id(JAGGAER_SUPPLIER_ID_1).name(JAGGAER_SUPPLIER_NAME_1).build();
    var supplier = Supplier.builder().companyData(companyData).build();
    var supplier1 = Supplier.builder().companyData(companyData1).build();
    var suppliersList =
        SuppliersList.builder().supplier(Arrays.asList(supplier, supplier1)).build();
    var rfxResponse = new ExportRfxResponse();
    rfxResponse.setSuppliersList(suppliersList);
    return rfxResponse;
  }

  @Test
  void testRPAProcessInputMapper() throws JsonProcessingException {
    var input = RPAProcessInput.builder().userName("John").password("vendol").ittCode("itt767");
    RPAProcessInput in = input.messageBody("MessageBody").build();
    String writeValueAsString = new ObjectMapper().writeValueAsString(in);
    log.info(writeValueAsString);
    var RPAGenericData = new RPAGenericData().setProcessInput(writeValueAsString);
    String writeValueAsString1 = new ObjectMapper().writeValueAsString(RPAGenericData);
    log.info(writeValueAsString1);

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
