package uk.gov.crowncommercial.dts.scale.cat.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.Message;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.SubUsers.SubUser;

@Service
@RequiredArgsConstructor
public class MessageService {

	private static final String CREATE_MESSAGE = "Create";

	private static final String RESPOND_MESSAGE = "Respond";

	private static final String PROCESS_NAME = "BuyerMessaging";

	private static final String PROFILE_NAME = "ITTEvaluation";

	private static final String SOURCE = "CaT";

	private static final Integer SOURCE_ID = 1;

	private final WebClient messageServiceWebClient;

	private final WebclientWrapper webclientWrapper;

	private final ValidationService validationService;

	private final UserProfileService userService;

	private final ObjectMapper objectMapper;

	private final JaggaerAPIConfig jaggaerAPIConfig;

	public Object broadcastMessage(String profile, Integer projectId, String eventId, Message message) {

		ProcurementEvent procurementEvent = validationService.validateProjectAndEventIds(projectId, eventId);

		Optional<SubUser> buyerUser = userService.resolveBuyerUserByEmail(profile);

		Map<String, Object> processInputMap = new LinkedHashMap<String, Object>();

		Map<String, Object> ocds = (LinkedHashMap<String, Object>) message.getOCDS();

		processInputMap.put("Search.Username", buyerUser.get().getEmail());
		processInputMap.put("Search.Password", "password");
		processInputMap.put("Search.ITTCode", procurementEvent.getEventID());
		processInputMap.put("Search.BroadcastMessage", message.getNonOCDS().getIsBroadcast());
		processInputMap.put("Search.MessagingAction", CREATE_MESSAGE);
		processInputMap.put("Search.MessageSubject", ocds.get("title"));
		processInputMap.put("Search.MessageBody", ocds.get("description"));
		processInputMap.put("Search.MessageClassification", message.getNonOCDS().getClassification().getValue());
		processInputMap.put("Search.SenderName", buyerUser.get().getEmail());

		// Fields to reply the message
		if (message.getNonOCDS().getParentId() != null) {
			//TODO get supplier details using parentId
			processInputMap.put("Search.SupplierName", "supplier-name");
			processInputMap.put("Search.MessagingAction", RESPOND_MESSAGE);
			processInputMap.put("Search.MessageReceivedDate", "message-receive-date");
		}

		Object callJaggaerMessageAPI = callJaggaerMessageAPI(processInputMap);

		return callJaggaerMessageAPI;

	}

	private Object callJaggaerMessageAPI(Map<String, Object> processInputMap) {
		
		String processInput = makeProcessInput(processInputMap);
		
		Map<String, Object> postData = new LinkedHashMap<String, Object>();
		postData.put("processInput", processInput);
		postData.put("processName", PROCESS_NAME);
		postData.put("profileName", PROFILE_NAME);
		postData.put("source", SOURCE);
		postData.put("sourceId", SOURCE_ID);
		postData.put("retry", false);
		postData.put("requestTimeout", 3600000);
		postData.put("isSync", true);

		Object o = webclientWrapper.postData(postData, Object.class,
				messageServiceWebClient.mutate().defaultHeader("Authorization", "Bearer " + getAccessToken()).build(),
				60, jaggaerAPIConfig.getMessageAPIUrl());

		return o;
	}

	private String makeProcessInput(Map<String, Object> processInputMap) {
		
		String processInput = null;
		try {
			processInput = objectMapper.writeValueAsString(processInputMap);
		} catch (JsonProcessingException e) {
			throw new RuntimeException("Invalid process input fields");
		}
		
		return processInput.replaceAll("\"", "\\\\\"");
	}

	private String getAccessToken() {
		
		Map<String, String> jaggerRPACredentials = new LinkedHashMap<String, String>();
		
		jaggerRPACredentials.put("username", "testuser");
		jaggerRPACredentials.put("password", "rpa@123");
		String uriTemplate = "https://azd-uk-catrpa.uksouth.cloudapp.azure.com/api/token";
		
		return webclientWrapper.postData(jaggerRPACredentials, String.class, messageServiceWebClient, 60, uriTemplate);
	}
}
