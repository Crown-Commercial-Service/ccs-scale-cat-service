package uk.gov.crowncommercial.dts.scale.cat.service;

import static java.time.Duration.ofSeconds;
import static java.util.Optional.ofNullable;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig.ENDPOINT;

import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.configurationprocessor.json.JSONArray;
import org.springframework.boot.configurationprocessor.json.JSONException;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.config.RPAAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.exception.JaggaerApplicationException;
import uk.gov.crowncommercial.dts.scale.cat.exception.JaggaerRPAException;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.Message;
import uk.gov.crowncommercial.dts.scale.cat.model.rpa.AutomationOutputData;

/**
 *
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MessageService {

	private static final String CREATE_MESSAGE = "Create";

	private static final String RESPOND_MESSAGE = "Respond";

	private static final String PROCESS_NAME = "BuyerMessaging";

	private static final String PROFILE_NAME = "ITTEvaluation";

	private static final String SOURCE = "CaT";

	private static final String SOURCE_ID = "1";

	private final WebClient rpaServiceWebClient;

	private final WebclientWrapper webclientWrapper;

	private final ValidationService validationService;

	private final UserProfileService userService;

	private final ObjectMapper objectMapper;

	private final RPAAPIConfig rpaAPIConfig;

	private final JaggaerAPIConfig jaggeApiConfig;

	/**
	 * Which sends outbound message to all suppliers and single supplier. And also
	 * responds supplier messages
	 * 
	 * @param profile
	 * @param projectId
	 * @param eventId
	 * @param message   {@link Message}
	 * @return
	 */
	public Object broadcastMessage(String profile, Integer projectId, String eventId, Message message) {

		var procurementEvent = validationService.validateProjectAndEventIds(projectId, eventId);

		var buyerUser = userService.resolveBuyerUserByEmail(profile);

		var ocds = (LinkedHashMap<String, String>) message.getOCDS();

		var nonOCDS = message.getNonOCDS();

		var processInputMap = new HashMap<String, String>();

		processInputMap.put("Search.Username", buyerUser.get().getEmail());
		processInputMap.put("Search.Password", rpaAPIConfig.getBuyerPwd());
		processInputMap.put("Search.ITTCode", /* procurementEvent.getExternalReferenceId() */"itt_86");
		processInputMap.put("Search.BroadcastMessage", nonOCDS.getIsBroadcast() ? "Yes" : "No");
		processInputMap.put("Search.MessagingAction", CREATE_MESSAGE);
		processInputMap.put("Search.MessageSubject", ocds.get("title"));
		processInputMap.put("Search.MessageBody", ocds.get("description"));
		processInputMap.put("Search.MessageClassification",
				/* nonOCDS.getClassification().getValue() */"Technical Clarification");
		processInputMap.put("Search.SenderName", buyerUser.get().getName());
		processInputMap.put("Search.SupplierName", "");
		processInputMap.put("Search.MessageReceivedDate", "");

		// Fields to reply the message
		if (nonOCDS.getParentId() != null) {
			// TODO get supplier details using parentId
			processInputMap.put("Search.SupplierName", "supplier-name");
			processInputMap.put("Search.MessagingAction", RESPOND_MESSAGE);
			processInputMap.put("Search.MessageReceivedDate", "message-receive-date");
		}

		return callRPAMessageAPI(processInputMap);

	}

	/**
	 * @param projectId
	 * @param eventId
	 * @param messageId
	 * @return
	 */
	public Object getMessage(Integer projectId, String eventId, String messageId) {

		validationService.validateProjectAndEventIds(projectId, eventId);

		var exportMessageUri = jaggeApiConfig.getMessage().get(ENDPOINT);

		var response = ofNullable(rpaServiceWebClient.get().uri(exportMessageUri, messageId).retrieve()
				.bodyToMono(Object.class).block(ofSeconds(jaggeApiConfig.getTimeoutDuration())))
						.orElseThrow(() -> new JaggaerApplicationException(INTERNAL_SERVER_ERROR.value(),
								"Unexpected error retrieving message"));

		// TODO segregate response object
		return response;
	}

	/**
	 * @param processInputMap
	 * @return
	 */
	private String callRPAMessageAPI(Map<String, String> processInputMap) {

		var processInput = makeProcessInput(processInputMap);

		var postData = new HashMap<String, Object>();
		postData.put("processInput", processInput);
		postData.put("processName", PROCESS_NAME);
		postData.put("profileName", PROFILE_NAME);
		postData.put("source", SOURCE);
		postData.put("sourceId", SOURCE_ID);
		postData.put("retry", false);
		postData.put("requestTimeout", 3600000);
		postData.put("isSync", true);

		var response = ofNullable(rpaServiceWebClient.post().uri(rpaAPIConfig.getAccessUrl())
				.headers(e -> e.setBearerAuth(getAccessToken())).bodyValue(postData).retrieve().bodyToMono(Object.class)
				.block(Duration.ofSeconds(60)))
						.orElseThrow(() -> new JaggaerApplicationException(INTERNAL_SERVER_ERROR.value(),
								"Unexpected error posting data to " + "uri template"));
		return validateResponse(response);
	}

	private String validateResponse(Object apiResponse) {

		try {

			var response = (LinkedHashMap<String, Object>) apiResponse;

			JSONObject json = new JSONObject(response);

			var responseString = (String) json.getJSONObject("response").get("response");

			Map<String, Object> convertedObject = convertStringToObject(responseString);

			json = new JSONObject(convertedObject);

			JSONArray jsonArray = json.getJSONArray("AutomationOutputData");

			AutomationOutputData automationData = new Gson().fromJson(jsonArray.get(1).toString(),
					AutomationOutputData.class);

			String status = automationData.getCviewDictionary().getStatus();

			log.info("Status of RPA API call : {} ", status);

			if (automationData.getCviewDictionary().getIsError().contentEquals("True")) {
				String errorDescription = automationData.getCviewDictionary().getErrorDescription();
				log.info("Error Description {} ", errorDescription);
				throw new JaggaerRPAException(errorDescription);
			}

			return (String) response.get("transactionId");
			
		} catch (JSONException e) {
			throw new RuntimeException(e.getMessage());
		}
	}

	/**
	 * @param processInputMap
	 * @return input as a string value
	 */
	private String makeProcessInput(Map<String, String> processInputMap) {

		String processInput = convertObjectToString(processInputMap);

		log.info("Process input {}", processInput);
		return processInput.replaceAll("\"", "\\\"");
	}

	private String convertObjectToString(Map<String, String> inputObject) {

		String outputString = null;
		try {
			outputString = objectMapper.writeValueAsString(inputObject);
		} catch (JsonProcessingException e) {
			throw new RuntimeException("Failed to convert Object to String");
		}
		return outputString;
	}

	private Map<String, Object> convertStringToObject(String inputString) {

		var outputObject = new HashMap<String, Object>();
		try {
			outputObject = objectMapper.readValue(inputString, outputObject.getClass());
		} catch (JsonProcessingException e) {
			throw new RuntimeException("Failed to convert String to Object");
		}
		return outputObject;
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

		return webclientWrapper.postData(jaggerRPACredentials, String.class, rpaServiceWebClient, 60, uriTemplate);
	}
}
