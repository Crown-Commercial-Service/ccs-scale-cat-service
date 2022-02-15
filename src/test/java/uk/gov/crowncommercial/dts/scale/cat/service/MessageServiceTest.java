package uk.gov.crowncommercial.dts.scale.cat.service;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.HashMap;
import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import uk.gov.crowncommercial.dts.scale.cat.config.RPAAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.Message;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.MessageNonOCDS;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.MessageNonOCDS.ClassificationEnum;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.MessageOCDS;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.SubUsers.SubUser;

/**
 * Service layer tests
 */
@SpringBootTest(classes = { MessageService.class }, webEnvironment = WebEnvironment.NONE)
@EnableConfigurationProperties(RPAAPIConfig.class)
class MessageServiceTest {

	private static final String PRINCIPAL = "venki@bric.org.uk";
	private static final String EVENT_OCID = "ocds-abc123-1";
	private static final Integer PROC_PROJECT_ID = 1;
	private static final String JAGGAER_USER_ID = "12345";

	private static final Optional<SubUser> JAGGAER_USER = Optional
			.of(SubUser.builder().userId(JAGGAER_USER_ID).email(PRINCIPAL).build());

	@MockBean(answer = Answers.RETURNS_DEEP_STUBS)
	private WebClient rpaServiceWebClient;

	@MockBean
	private UserProfileService userProfileService;

	@MockBean
	private ConclaveService conclaveService;

	@MockBean
	private ValidationService validationService;

	@MockBean
	private RPAAPIConfig rpaAPIConfig;

	@MockBean
	private WebclientWrapper webclientWrapper;

	@MockBean
	private AgreementsService agreementsService;

	@MockBean
	private MessageService messageService;

	@BeforeAll
	static void beforeAll() {
	}

	@Test
	void testSendMessageToAllSuppliers() throws Exception {

		MessageOCDS ocds = new MessageOCDS().title("Requesting Docs")
				.description("Please send the original documents to appprove request");

		MessageNonOCDS nonocds = new MessageNonOCDS().isBroadcast(true)
				.classification(ClassificationEnum.TECHNICAL_CLARIFICATION);

		var message = new Message();
		message.setOCDS(ocds);
		message.setNonOCDS(nonocds);

		var jaggerRPACredentials = new HashMap<String, String>();
		jaggerRPACredentials.put("username", rpaAPIConfig.getUserName());
		jaggerRPACredentials.put("password", rpaAPIConfig.getUserPwd());
		var uriTemplate = rpaAPIConfig.getAuthenticationUrl();

		// Mock behaviours
		when(userProfileService.resolveBuyerUserByEmail(PRINCIPAL)).thenReturn(JAGGAER_USER);

		when(validationService.validateProjectAndEventIds(PROC_PROJECT_ID, EVENT_OCID))
				.thenReturn(ProcurementEvent.builder().externalReferenceId("itt_8673").build());

		when(webclientWrapper.postData(jaggerRPACredentials, String.class, rpaServiceWebClient, 60, uriTemplate))
				.thenReturn("token");

		String responseString = "{\n"
				+ "		    \"processInput\": \"{\\\"Search.Username\\\":\\\"venki.bathula@brickendon.com\\\",\\\"Search.Password\\\":\\\"password\\\",\\\"Search.ITTCode\\\":\\\"itt_8673\\\",\\\"Search.BroadcastMessage\\\":\\\"Yes\\\",\\\"Search.MessagingAction\\\":\\\"Create\\\",\\\"Search.MessageSubject\\\":\\\"SIT Test\\\",\\\"Search.MessageBody\\\":\\\"Yes! we need a company registration document.\\\",\\\"Search.MessageClassification\\\":\\\"Technical Clarification\\\",\\\"Search.SupplierName\\\":\\\"\\\",\\\"Search.SenderName\\\":\\\"Venki Bathula\\\",\\\"Search.MessageReceivedDate\\\":\\\"\\\"}\",\n"
				+ "		    \"processName\": \"BuyerMessaging\",\n" + "		    \"profileName\": \"ITTEvaluation\",\n"
				+ "		    \"source\": \"Postman\",\n" + "		    \"sourceId\": \"1\",\n"
				+ "		    \"retry\": false,\n" + "		    \"retryConfigurations\": {\n"
				+ "		        \"previousRequestIds\": [],\n" + "		        \"noOfTimesRetry\": 0,\n"
				+ "		        \"retryCoolOffInterval\": 10000\n" + "		    },\n"
				+ "		    \"requestTimeout\": 3600000,\n" + "		    \"isSync\": true,\n"
				+ "		    \"transactionId\": \"ab85bb4f-0204-4e3b-a2fa-f69cbb0e4134\",\n"
				+ "		    \"status\": \"success\",\n" + "		    \"response\": {\n"
				+ "		        \"response\": \"{\\\"AutomationOutputData\\\":[{\\\"AppName\\\":\\\"CCS\\\",\\\"CviewDictionary\\\":{}},{\\\"AppName\\\":\\\"Microbot : API_ResponsePayload\\\",\\\"CviewDictionary\\\":{\\\"ErrorDescription\\\":\\\"\\\",\\\"IsError\\\":\\\"False\\\",\\\"Status\\\":\\\"Create message for itt_8673 completed by venki.bathula@brickendon.com\\\"}}],\\\"HttpStatus\\\":\\\"\\\",\\\"ParentRequest\\\":{\\\"AutomationInputDictionary\\\":{\\\"Search.Username\\\":\\\"venki.bathula@brickendon.com\\\",\\\"Search.Password\\\":\\\"password\\\",\\\"Search.ITTCode\\\":\\\"itt_8673\\\",\\\"Search.BroadcastMessage\\\":\\\"Yes\\\",\\\"Search.MessagingAction\\\":\\\"Create\\\",\\\"Search.MessageSubject\\\":\\\"SIT Test\\\",\\\"Search.MessageBody\\\":\\\"Yes! we need a company registration document.\\\",\\\"Search.MessageClassification\\\":\\\"Technical Clarification\\\",\\\"Search.SupplierName\\\":\\\"\\\",\\\"Search.SenderName\\\":\\\"Venki Bathula\\\",\\\"Search.MessageReceivedDate\\\":\\\"\\\"},\\\"MaskedAutomationInputDictionary\\\":{\\\"Search.Username\\\":\\\"venki.bathula@brickendon.com\\\",\\\"Search.Password\\\":\\\"HPSW0Mx6ZRMXo/ok+T/2u/+BY5gjR1ajS+LgqtyPSRYz/kXgKywqh4AeYduOpypcshDH82OEF6osmnj0b3gNYg==\\\",\\\"Search.ITTCode\\\":\\\"itt_8673\\\",\\\"Search.BroadcastMessage\\\":\\\"Yes\\\",\\\"Search.MessagingAction\\\":\\\"Create\\\",\\\"Search.MessageSubject\\\":\\\"SIT Test\\\",\\\"Search.MessageBody\\\":\\\"Yes! we need a company registration document.\\\",\\\"Search.MessageClassification\\\":\\\"Technical Clarification\\\",\\\"Search.SupplierName\\\":\\\"\\\",\\\"Search.SenderName\\\":\\\"Venki Bathula\\\",\\\"Search.MessageReceivedDate\\\":\\\"\\\"},\\\"AnonymizedAutomationInputDictionary\\\":{\\\"Search.Username\\\":\\\"venki.bathula@brickendon.com\\\",\\\"Search.Password\\\":\\\"HPSW0Mx6ZRMXo/ok+T/2u/+BY5gjR1ajS+LgqtyPSRYz/kXgKywqh4AeYduOpypcshDH82OEF6osmnj0b3gNYg==\\\",\\\"Search.ITTCode\\\":\\\"itt_8673\\\",\\\"Search.BroadcastMessage\\\":\\\"Yes\\\",\\\"Search.MessagingAction\\\":\\\"Create\\\",\\\"Search.MessageSubject\\\":\\\"SIT Test\\\",\\\"Search.MessageBody\\\":\\\"Yes! we need a company registration document.\\\",\\\"Search.MessageClassification\\\":\\\"Technical Clarification\\\",\\\"Search.SupplierName\\\":\\\"\\\",\\\"Search.SenderName\\\":\\\"Venki Bathula\\\",\\\"Search.MessageReceivedDate\\\":\\\"\\\"},\\\"DataProtectionErrors\\\":{},\\\"ProcessName\\\":\\\"BuyerMessaging\\\",\\\"ProfileName\\\":\\\"ITTEvaluation\\\",\\\"RetryFlag\\\":false,\\\"Retry\\\":{\\\"NoOfTimesRetry\\\":0,\\\"RetryCoolOffInterval\\\":10000,\\\"PreviousRequestIds\\\":[]},\\\"APIVersion\\\":\\\"\\\",\\\"AppId\\\":\\\"\\\",\\\"CommandExecutionWindow\\\":\\\"\\\",\\\"CommandGenerationSource\\\":\\\"\\\",\\\"Country\\\":\\\"\\\",\\\"Instance\\\":\\\"\\\",\\\"PartnerId\\\":\\\"\\\",\\\"ReferenceCode\\\":\\\"\\\",\\\"Timestamp\\\":\\\"2022-02-15T22:21:46.190Z\\\",\\\"UserName\\\":\\\"testuser\\\",\\\"VID\\\":null,\\\"SENodeId\\\":null,\\\"RequestExpiration\\\":\\\"3600000\\\",\\\"wait_for_completion\\\":\\\"true\\\",\\\"Impersonation\\\":null,\\\"IfExternalVaultType\\\":false},\\\"ReferenceCode\\\":\\\"\\\",\\\"RequestExecutionTime\\\":\\\"00:00:14.6458043\\\",\\\"SEReferenceCode\\\":\\\"\\\",\\\"SpareParam1\\\":\\\"\\\",\\\"SpareParam2\\\":\\\"\\\",\\\"UserName\\\":\\\"testuser\\\",\\\"CommandResultEnum\\\":0,\\\"CommandResultDescription\\\":\\\"SearchSuccessful\\\",\\\"ErrorDetailObject\\\":[],\\\"SENodeId\\\":null,\\\"RequestReceivedTime\\\":\\\"2022-02-15T22:21:46.2043795Z\\\",\\\"RequestProcessedTime\\\":\\\"2022-02-15T22:22:00.8501838Z\\\",\\\"TransactionDescription\\\":\\\"SearchSuccessful\\\"}\"\n"
				+ "		    },\n" + "		    \"error\": {}\n" + "		}\"";

		Object responseObject = new ObjectMapper().readValue(responseString, Object.class);

		when(rpaServiceWebClient.post().uri(rpaAPIConfig.getAccessUrl()).headers(e -> e.setBearerAuth("token"))
				.bodyValue(any()).retrieve().bodyToMono(Object.class).block(Duration.ofSeconds(60)))
						.thenReturn(responseObject);

		// Invoke
		var messageResponse = messageService.sendOrRespondMessage(PRINCIPAL, PROC_PROJECT_ID, EVENT_OCID, message);

		// Assert
		assertNotNull(messageResponse);
	}

}
