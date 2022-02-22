package uk.gov.crowncommercial.dts.scale.cat.service;

import static org.springframework.util.CollectionUtils.isEmpty;

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

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.config.RPAAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.exception.JaggaerRPAException;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.OrganisationMapping;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.Message;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.MessageNonOCDS;
import uk.gov.crowncommercial.dts.scale.cat.model.rpa.AutomationOutputData;
import uk.gov.crowncommercial.dts.scale.cat.model.rpa.RPAAPIResponse;
import uk.gov.crowncommercial.dts.scale.cat.model.rpa.RPAGenericData;
import uk.gov.crowncommercial.dts.scale.cat.model.rpa.RPAProcessInput;
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
	 * @throws JsonProcessingException
	 */
	public String sendOrRespondMessage(String profile, Integer projectId, String eventId, Message message) {
		var procurementEvent = validationService.validateProjectAndEventIds(projectId, eventId);
		var buyerUser = userService.resolveBuyerUserByEmail(profile);
		var ocds = message.getOCDS();
		var nonOCDS = message.getNonOCDS();

		// Creating RPA process input string
		var inputBuilder = RPAProcessInput.builder().userName(buyerUser.get().getEmail())
				.password(rpaAPIConfig.getBuyerPwd()).ittCode(procurementEvent.getExternalReferenceId())
				.broadcastMessage(nonOCDS.getIsBroadcast() ? "Yes" : "No").messagingAction(CREATE_MESSAGE)
				.messageSubject(ocds.getTitle()).messageBody(ocds.getDescription())
				.messageClassification(nonOCDS.getClassification().getValue()).senderName(buyerUser.get().getName())
				.supplierName("").messageReceivedDate("");

		// To reply the message
		if (nonOCDS.getParentId() != null) {
			// TODO get message details using parentId
			inputBuilder.messagingAction(RESPOND_MESSAGE).messageReceivedDate("message-receive-date");
		}

		// Adding supplier details
		if (Boolean.FALSE.equals(nonOCDS.getIsBroadcast())) {
			if (!CollectionUtils.isEmpty(nonOCDS.getReceiverList())) {
				var supplierString = validateSuppliers(procurementEvent, nonOCDS);
				log.info("Suppliers list: {}", supplierString);
				inputBuilder.supplierName(supplierString);
			} else {
				throw new JaggaerRPAException("Suppliers are mandatory if broadcast is 'No'");
			}
		}

		return callRPAMessageAPI(inputBuilder.build());
	}

	/**
	 * @param procurementEvent
	 * @param nonOCDS
	 * @return suppliers as a string
	 */
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
	 * @return rpa status
	 * @throws JsonProcessingException
	 */
	@SneakyThrows(JsonProcessingException.class)
	private String callRPAMessageAPI(RPAProcessInput processInput) {
		var request = new RPAGenericData();
		request.setProcessInput(objectMapper.writeValueAsString(processInput))
				.setProcessName(RPAProcessNameEnum.BUYER_MESSAGING.getValue())
				.setProfileName(rpaAPIConfig.getProfileName()).setSource(rpaAPIConfig.getSource())
				.setSourceId(rpaAPIConfig.getSourceId()).setRequestTimeout(rpaAPIConfig.getRequestTimeout())
				.setSync(true);
		log.info("RPA Request: {}", objectMapper.writeValueAsString(request));

		var response = webclientWrapper.postDataWithToken(request, RPAAPIResponse.class, rpaServiceWebClient,
				rpaAPIConfig.getTimeoutDuration(), rpaAPIConfig.getAccessUrl(), getAccessToken());

		return validateResponse(response);
	}

	/**
	 * Validate RPA API Response
	 * 
	 * @param apiResponse
	 * @return rpa api status
	 */
	@SneakyThrows({ JsonProcessingException.class, JSONException.class })
	private String validateResponse(RPAAPIResponse apiResponse) {
		var convertedObject = convertStringToObject(apiResponse.getResponse().getResponse());
		var json = new JSONObject(convertedObject);
		var jsonArray = json.getJSONArray("AutomationOutputData");
		var automationData = objectMapper.readValue(jsonArray.get(1).toString(), AutomationOutputData.class);

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
	@SneakyThrows({ JsonProcessingException.class })
	private Map<String, Object> convertStringToObject(String inputString) {
		var outputObject = new HashMap<String, Object>();
		outputObject = objectMapper.readValue(inputString, new TypeReference<HashMap<String, Object>>() {
		});
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