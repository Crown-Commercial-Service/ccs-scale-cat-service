package uk.gov.crowncommercial.dts.scale.cat.service;

import static java.util.Optional.ofNullable;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.collections.CollectionUtils;
import org.springframework.boot.configurationprocessor.json.JSONArray;
import org.springframework.boot.configurationprocessor.json.JSONException;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.config.RPAAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.exception.JaggaerRPAException;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.Message;
import uk.gov.crowncommercial.dts.scale.cat.model.rpa.AutomationOutputData;
import uk.gov.crowncommercial.dts.scale.cat.model.rpa.RPAProcessNameEnum;

/**
 *
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MessageService {

	private static final String CREATE_MESSAGE = "Create";

	private static final String RESPOND_MESSAGE = "Respond";

	private final WebClient rpaServiceWebClient;

	private final WebclientWrapper webclientWrapper;

	private final ValidationService validationService;

	private final UserProfileService userService;

	private final ObjectMapper objectMapper;

	private final RPAAPIConfig rpaAPIConfig;

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
	public String sendOrRespondMessage(String profile, Integer projectId, String eventId, Message message) {

		var procurementEvent = validationService.validateProjectAndEventIds(projectId, eventId);
		var buyerUser = userService.resolveBuyerUserByEmail(profile);
		var ocds = message.getOCDS();
		var nonOCDS = message.getNonOCDS();

		// Creating RPA process input string
		var processInputMap = new HashMap<String, String>();
		processInputMap.put("Search.Username", buyerUser.get().getEmail());
		processInputMap.put("Search.Password", rpaAPIConfig.getBuyerPwd());
		processInputMap.put("Search.ITTCode", /* procurementEvent.getExternalReferenceId() */"itt_8673");
		processInputMap.put("Search.BroadcastMessage", nonOCDS.getIsBroadcast() ? "Yes" : "No");
		processInputMap.put("Search.MessagingAction", CREATE_MESSAGE);
		processInputMap.put("Search.MessageSubject", ocds.getTitle());
		processInputMap.put("Search.MessageBody", ocds.getDescription());
		processInputMap.put("Search.MessageClassification", nonOCDS.getClassification().getValue());
		processInputMap.put("Search.SenderName", buyerUser.get().getName());
		processInputMap.put("Search.SupplierName", "");
		processInputMap.put("Search.MessageReceivedDate", "");

		// Fields to reply the message
		if (nonOCDS.getParentId() != null) {
			// TODO get message details using parentId
			processInputMap.put("Search.MessagingAction", RESPOND_MESSAGE);
			processInputMap.put("Search.MessageReceivedDate", "message-receive-date");
		}

		StringBuilder appendSupplierList = new StringBuilder();
		if (!nonOCDS.getIsBroadcast() && !CollectionUtils.isEmpty(nonOCDS.getReceiverList())) {

			nonOCDS.getReceiverList().stream().forEach(supplier -> {
				// TODO get supplier details by using org ref
				appendSupplierList.append(supplier.getName());
				appendSupplierList.append(";");
			});

			String suppliers = appendSupplierList.toString().substring(0, appendSupplierList.toString().length() - 1);
			log.info("Suppliers list: {}", suppliers);
			processInputMap.put("Search.SupplierName", suppliers);
		}

		return callRPAMessageAPI(processInputMap);

	}

	/**
	 * @param processInputMap
	 * @return
	 */
	private String callRPAMessageAPI(Map<String, String> processInputMap) {

		var processInput = makeProcessInput(processInputMap);

		var postData = new HashMap<String, Object>();
		postData.put("processInput", processInput);
		postData.put("processName", RPAProcessNameEnum.BUYER_MESSAGING.getValue());
		postData.put("profileName", rpaAPIConfig.getProfileName());
		postData.put("source", rpaAPIConfig.getSource());
		postData.put("sourceId", rpaAPIConfig.getSourceId());
		postData.put("retry", false);
		postData.put("requestTimeout", 3600000);
		postData.put("isSync", true);

		var response = ofNullable(rpaServiceWebClient.post().uri(rpaAPIConfig.getAccessUrl())
				.headers(e -> e.setBearerAuth(getAccessToken())).bodyValue(postData).retrieve().bodyToMono(Object.class)
				.block(Duration.ofSeconds(60)))
						.orElseThrow(() -> new JaggaerRPAException(INTERNAL_SERVER_ERROR.value(),
								"Unexpected error posting data to " + "uri template"));
		return validateResponse(response);
	}

	/**
	 * Validate RPA API Response
	 * 
	 * @param apiResponse
	 * @return
	 */
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

	/**
	 * Convert object to String
	 * 
	 * @param inputObject
	 * @return
	 */
	private String convertObjectToString(Map<String, String> inputObject) {

		String outputString = null;
		try {
			outputString = objectMapper.writeValueAsString(inputObject);
		} catch (JsonProcessingException e) {
			throw new RuntimeException("Failed to convert Object to String");
		}
		return outputString;
	}

	/**
	 * Convert String to Object
	 * 
	 * @param inputString
	 * @return
	 */
	private Map<String, Object> convertStringToObject(String inputString) {

		var outputObject = new HashMap<String, Object>();
		try {
			outputObject = objectMapper.readValue(inputString, new TypeReference<HashMap<String, Object>>() {
			});
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
