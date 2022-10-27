package uk.gov.crowncommercial.dts.scale.cat.service;

import static org.springframework.util.CollectionUtils.isEmpty;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.data.util.Pair;
import org.springframework.scheduling.annotation.Async;
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
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.Supplier;
import uk.gov.crowncommercial.dts.scale.cat.model.rpa.*;
import uk.gov.crowncommercial.dts.scale.cat.repo.BuyerUserDetailsRepo;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;

@Service
@RequiredArgsConstructor
@Slf4j
public class RPAGenericService {

  private final WebClient rpaServiceWebClient;
  private final WebclientWrapper webclientWrapper;
  private final ObjectMapper objectMapper;
  private final RPAAPIConfig rpaAPIConfig;
  private final JaggaerService jaggaerService;
  private final RetryableTendersDBDelegate retryableTendersDBDelegate;
  private final BuyerUserDetailsRepo buyerDetailsRepo;

  /**
   * @param procurementEvent
   * @param nonOCDS
   * @return suppliers as a string
   */
  public Pair<List<Supplier>, Set<OrganisationMapping>> getValidSuppliers(
      final ProcurementEvent procurementEvent, final List<String> orgIds) {
    var supplierOrgIds = orgIds.stream().collect(Collectors.toSet());
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
    var suppliers = jaggaerService.getRfxWithSuppliers(procurementEvent.getExternalEventId()).getSuppliersList()
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

    return Pair.of(matchedSuppliers, supplierOrgMappings);
  }

  /**
   * @param processInputMap
   * @return rpa status
   * @throws JsonProcessingException
   */
  @SneakyThrows
  public String callRPAMessageAPI(final RPAProcessInput processInput,
      final RPAProcessNameEnum processName) {
    var request = new RPAGenericData();
    request.setProcessInput(objectMapper.writeValueAsString(processInput))
        .setProcessName(processName.getValue()).setProfileName(rpaAPIConfig.getProfileName())
        .setSource(rpaAPIConfig.getSource()).setSourceId(rpaAPIConfig.getSourceId())
        .setRequestTimeout(rpaAPIConfig.getRequestTimeout()).setSync(true);
    log.info("RPA Request: {}", request.toString());

    String accessToken=getAccessToken();


    var response =
        webclientWrapper.postDataWithToken(request, RPAAPIResponse.class, rpaServiceWebClient,
            rpaAPIConfig.getTimeoutDuration(), rpaAPIConfig.getAccessUrl(),accessToken);
    log.info("RPA Response: {}", objectMapper.writeValueAsString(response));

    return validateResponse(response);

  }

  /**
   * @param processInputMap
   * @return rpa status
   * @throws JsonProcessingException
   */
  @SneakyThrows
  @Async
  public String asyncCallRPAMessageAPI(final RPAProcessInput processInput,
      final RPAProcessNameEnum processName) {
    var request = new RPAGenericData();
    request.setProcessInput(objectMapper.writeValueAsString(processInput))
        .setProcessName(processName.getValue()).setProfileName(rpaAPIConfig.getProfileName())
        .setSource(rpaAPIConfig.getSource()).setSourceId(rpaAPIConfig.getSourceId())
        .setRequestTimeout(rpaAPIConfig.getRequestTimeout()).setSync(true);
    log.info("{} - RPA Request: {}", Thread.currentThread(), request.toString());


    String accessToken=getAccessToken();

    var response =
        webclientWrapper.postDataWithToken(request, RPAAPIResponse.class, rpaServiceWebClient,
            rpaAPIConfig.getTimeoutDuration(), rpaAPIConfig.getAccessUrl(), accessToken);
    log.info("{} - RPA Response: {}", Thread.currentThread(), response.toString());

    return "Ok";
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
   * Validate RPA API Response
   *
   * @param apiResponse
   * @return rpa api status
   */
  @SneakyThrows
  private String validateResponse(final RPAAPIResponse apiResponse) {
    var convertedObject = convertStringToObject(apiResponse.getResponse().getResponse());
    var maps = (List<Map<String, String>>) convertedObject.get("AutomationOutputData");
    var responseList = new ArrayList<AutomationOutputData>();
    for (Map<String, String> map : maps) {
      var automationData =
          objectMapper.readValue(objectMapper.writeValueAsString(map), AutomationOutputData.class);
      responseList.add(automationData);
    }
    var automationFilterData = responseList.stream()
        .filter(e -> e.getAppName().contentEquals("Microbot : API_ResponsePayload")).findFirst();
    if (automationFilterData.isPresent()) {
      var automationData = automationFilterData.get();
      var status = automationData.getCviewDictionary().getStatus();
      log.info("Status of RPA API call : {} ", status);

      if (automationData.getCviewDictionary().getIsError().contentEquals("True")) {
        var errorDescription = automationData.getCviewDictionary().getErrorDescription();
        log.info("Error Description {} ", errorDescription);
        throw new JaggaerRPAException(errorDescription);
      }
      return status;
    }
    return "Invalid Response";
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

  public String getBuyerEncryptedPassword(String userId) {
    var buyerDetails = buyerDetailsRepo.findById(userId);
    if (buyerDetails.isEmpty()) {
      throw new JaggaerRPAException("Buyer encrypted password not found");
    }
    return buyerDetails.get().getUserPassword();
  }

  public OffsetDateTime handleDSTDate(OffsetDateTime offsetDate, String zoneId) {
    var zonedDateTimeBefore = offsetDate.atZoneSameInstant(ZoneId.of(zoneId));
    return zonedDateTimeBefore.toOffsetDateTime();
  }
}
