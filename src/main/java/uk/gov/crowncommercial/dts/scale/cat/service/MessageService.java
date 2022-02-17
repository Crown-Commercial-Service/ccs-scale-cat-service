package uk.gov.crowncommercial.dts.scale.cat.service;

import static java.util.Optional.ofNullable;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.util.CollectionUtils.isEmpty;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.collections.CollectionUtils;
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
import uk.gov.crowncommercial.dts.scale.cat.model.entity.OrganisationMapping;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.Message;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.MessageNonOCDS;
import uk.gov.crowncommercial.dts.scale.cat.model.rpa.AutomationOutputData;
import uk.gov.crowncommercial.dts.scale.cat.model.rpa.RPAAPIResponse;
import uk.gov.crowncommercial.dts.scale.cat.model.rpa.RPAProcessNameEnum;
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

	private final WebClient rpaServiceWebClient;

	private final WebclientWrapper webclientWrapper;

	private final ValidationService validationService;

	private final UserProfileService userService;

	private final ObjectMapper objectMapper;

	private final RPAAPIConfig rpaAPIConfig;

	private final JaggaerService jaggaerService;

	private final RetryableTendersDBDelegate retryableTendersDBDelegate;

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
		processInputMap.put("Search.ITTCode", procurementEvent.getExternalReferenceId());
		processInputMap.put("Search.BroadcastMessage", nonOCDS.getIsBroadcast() ? "Yes" : "No");
		processInputMap.put("Search.MessagingAction", CREATE_MESSAGE);
		processInputMap.put("Search.MessageSubject", ocds.getTitle());
		processInputMap.put("Search.MessageBody", ocds.getDescription());
		processInputMap.put("Search.MessageClassification", nonOCDS.getClassification().getValue());
		processInputMap.put("Search.SenderName", buyerUser.get().getName());
		processInputMap.put("Search.SupplierName", "");
		processInputMap.put("Search.MessageReceivedDate", "");

		// To reply the message
		if (nonOCDS.getParentId() != null) {
			// TODO get message details using parentId
			processInputMap.put("Search.MessagingAction", RESPOND_MESSAGE);
			processInputMap.put("Search.MessageReceivedDate", "message-receive-date");
		}

		// Adding supplier details
		if (!nonOCDS.getIsBroadcast()) {
			if (!CollectionUtils.isEmpty(nonOCDS.getReceiverList())) {
				var supplierString = validateSuppliers(procurementEvent, nonOCDS);
				log.info("Suppliers list: {}", supplierString);
				processInputMap.put("Search.SupplierName", supplierString);
			} else {
				throw new JaggaerRPAException("Suppliers are mandatory if broadcast is 'Yes'");
			}
		}

		return callRPAMessageAPI(processInputMap);

	}

	private String validateSuppliers(ProcurementEvent procurementEvent, MessageNonOCDS nonOCDS) {

		// ignoring string content from organisation Ids
		var supplierOrgIds = nonOCDS.getReceiverList().stream().map(ls -> ls.getId().replace("GB-COH-", ""))
				.collect(Collectors.toSet());

		// Retrieve and verify Tenders DB org mappings
		var supplierOrgMappings = retryableTendersDBDelegate.findOrganisationMappingByOrganisationIdIn(supplierOrgIds);
		if (isEmpty(supplierOrgMappings)) {
			String errorDesc = String.format("No supplier organisation mappings found in Tenders DB %s",
					supplierOrgIds);
			log.error(errorDesc);
			throw new JaggaerRPAException(errorDesc);
		}

		// Find all externalOrgIds
		var supplierExternalIds = supplierOrgMappings.stream().map(OrganisationMapping::getExternalOrganisationId)
				.collect(Collectors.toSet());

		// Find actual suppliers of project and event
		var suppliers = jaggaerService.getRfx(procurementEvent.getExternalEventId()).getSuppliersList().getSupplier();

		// Find all unmatched org ids
		var unMatchedSuppliers = supplierExternalIds.stream().filter(
				orgid -> suppliers.stream().noneMatch(supplier -> supplier.getCompanyData().getId().equals(orgid)))
				.collect(Collectors.toList());

		if (!isEmpty(unMatchedSuppliers)) {
			String errorDesc = String.format("Supplied organisation mappings not matched with actual suppliers '%s'",
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
				.header("Authorization", "Bearer " + getAccessToken()).bodyValue(postData).retrieve()
				.bodyToMono(RPAAPIResponse.class).block(Duration.ofSeconds(rpaAPIConfig.getTimeoutDuration())))
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
	private String validateResponse(RPAAPIResponse apiResponse) {
		try {
			var convertedObject = convertStringToObject(apiResponse.getResponse().getResponse());
			var json = new JSONObject(convertedObject);
			var jsonArray = json.getJSONArray("AutomationOutputData");
			var automationData = new Gson().fromJson(jsonArray.get(1).toString(), AutomationOutputData.class);

			var status = automationData.getCviewDictionary().getStatus();
			log.info("Status of RPA API call : {} ", status);

			if (automationData.getCviewDictionary().getIsError().contentEquals("True")) {
				var errorDescription = automationData.getCviewDictionary().getErrorDescription();
				log.info("Error Description {} ", errorDescription);
				throw new JaggaerRPAException(errorDescription);
			}
			return status;
		} catch (JSONException e) {
			throw new RuntimeException(e.getMessage());
		}
	}

	/**
	 * @param processInputMap
	 * @return input as a string value
	 */
	public String makeProcessInput(Map<String, String> processInputMap) {
		var processInput = convertObjectToString(processInputMap);
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
		String token = webclientWrapper.postData(jaggerRPACredentials, String.class, rpaServiceWebClient,
				rpaAPIConfig.getTimeoutDuration(), uriTemplate);
		return token;
	}
}
