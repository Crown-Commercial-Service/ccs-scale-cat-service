package uk.gov.crowncommercial.dts.scale.cat.service;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;
import java.util.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.web.reactive.function.client.WebClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.config.RPAAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.LotSupplier;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.Organization;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.BuyerUserDetails;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.OrganisationMapping;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementProject;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.Award2AllOf;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.AwardState;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.OrganizationReference1;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.CompanyData;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.ExportRfxResponse;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.SubUsers.SubUser;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.Supplier;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.SuppliersList;
import uk.gov.crowncommercial.dts.scale.cat.model.rpa.RPAAPIResponse;
import uk.gov.crowncommercial.dts.scale.cat.model.rpa.RPAGenericData;
import uk.gov.crowncommercial.dts.scale.cat.model.rpa.RPAProcessInput;
import uk.gov.crowncommercial.dts.scale.cat.model.rpa.RPAProcessInput.RPAProcessInputBuilder;
import uk.gov.crowncommercial.dts.scale.cat.model.rpa.RPAProcessNameEnum;
import uk.gov.crowncommercial.dts.scale.cat.repo.BuyerUserDetailsRepo;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;

/**
 *
 */
@SpringBootTest(classes = {AwardService.class, RPAGenericService.class},
    webEnvironment = WebEnvironment.NONE)
@Slf4j
@EnableConfigurationProperties({RPAAPIConfig.class})
@ContextConfiguration(classes = {ObjectMapper.class})
class AwardServiceTest {

  static final String AGREEMENT_NUMBER = "RM1234";
  static final String LOT_NUMBER = "Lot 2";

  static final String SUPPLIER_ORG_ID_1 = "1234567";
  static final LotSupplier LOT_SUPPLIER_1 = LotSupplier.builder()
      .organization(Organization.builder().id(SUPPLIER_ORG_ID_1).build()).build();
  static final String SUPPLIER_ORG_ID_2 = "7654321";
  static final LotSupplier LOT_SUPPLIER_2 = LotSupplier.builder()
      .organization(Organization.builder().id(SUPPLIER_ORG_ID_2).build()).build();

  static final Integer EXT_ORG_ID_1 = 2345678;
  static final Integer EXT_ORG_ID_2 = 8765432;
  static final OrganisationMapping ORG_MAPPING_1 = OrganisationMapping.builder().build();
  static final OrganisationMapping ORG_MAPPING_2 = OrganisationMapping.builder().build();

  static final Supplier EXT_SUPPLIER_1 =
      Supplier.builder().companyData(CompanyData.builder().id(EXT_ORG_ID_1).build()).build();
  static final Supplier EXT_SUPPLIER_2 =
      Supplier.builder().companyData(CompanyData.builder().id(EXT_ORG_ID_2).build()).build();

  private static final String PRINCIPAL = "venki@bric.org.uk";
  private static final String BUYER_USER_NAME = "Venki Bathula";
  private static final String BUYER_PASSWORD = "PASS12345";
  private static final String EVENT_OCID = "ocds-abc123-1";
  private static final Integer PROC_PROJECT_ID = 1;
  private static final String JAGGAER_USER_ID = "12345";
  private static final String EXTERNAL_EVENT_ID = "itt_8673";

  private static final String RFX_ID = "rfq_0001";
  private static final Integer JAGGAER_SUPPLIER_ID = 2345678;
  private static final String JAGGAER_SUPPLIER_NAME = "Bathula Consulting";

  private static final Integer JAGGAER_SUPPLIER_ID_1 = 8765432;
  private static final String JAGGAER_SUPPLIER_NAME_1 = "Doshi Industries";
  private static final String PRE_AWARD = "Pre-Award";

  static final ProcurementProject project = ProcurementProject.builder().build();

  private static final Optional<SubUser> JAGGAER_USER = Optional
      .of(SubUser.builder().userId(JAGGAER_USER_ID).email(PRINCIPAL).name(BUYER_USER_NAME).build());

  @MockBean
  private AgreementsService agreementsService;

  @MockBean
  private RetryableTendersDBDelegate retryableTendersDBDelegate;

  @Autowired
  private AwardService awardService;

  @MockBean
  private UserProfileService userProfileService;

  @MockBean
  private ValidationService validationService;

  @Autowired
  private RPAAPIConfig rpaAPIConfig;

  @MockBean
  private JaggaerService jaggaerService;

  @MockBean
  private WebclientWrapper webclientWrapper;

  @MockBean(answer = Answers.RETURNS_DEEP_STUBS)
  private WebClient rpaServiceWebClient;

  @MockBean
  private BuyerUserDetailsRepo buyerDetailsRepo;

  private static RPAGenericData request = new RPAGenericData();
  private RPAProcessInputBuilder inputBuilder = RPAProcessInput.builder();

  @BeforeAll
  static void beforeClass() {
    ORG_MAPPING_1.setOrganisationId(SUPPLIER_ORG_ID_1);
    ORG_MAPPING_1.setExternalOrganisationId(EXT_ORG_ID_1);
    ORG_MAPPING_2.setOrganisationId(SUPPLIER_ORG_ID_2);
    ORG_MAPPING_2.setExternalOrganisationId(EXT_ORG_ID_2);
  }

  @Test
  void testcreatePreAward() throws JsonProcessingException {
    // Stub some objects
    List<OrganizationReference1> suppliersList = new ArrayList<OrganizationReference1>();
    suppliersList.add(new OrganizationReference1().id("GB-COH-1234567"));

    var award = new Award2AllOf().suppliers(suppliersList);
    var responseString = "{\n"
        + "    \"processInput\": \"{\\\"Search.Username\\\":\\\"venki.bathula@brickendon.com\\\",\\\"Search.Password\\\":\\\"BFC9yX2TRFxMNa7f6vZLzg==\\\",\\\"Search.ITTCode\\\":\\\"itt_12216\\\",\\\"Search.SupplierName\\\":\\\"Bathula Consulting\\\",\\\"Search.AwardingAction\\\":\\\"Pre-Award\\\"}\",\n"
        + "    \"processName\": \"Awarding\",\n" + "    \"profileName\": \"ITTEvaluation\",\n"
        + "    \"source\": \"Postman\",\n" + "    \"sourceId\": \"1\",\n"
        + "    \"retry\": false,\n" + "    \"retryConfigurations\": {\n"
        + "        \"previousRequestIds\": [],\n" + "        \"noOfTimesRetry\": 0,\n"
        + "        \"retryCoolOffInterval\": 10000\n" + "    },\n"
        + "    \"requestTimeout\": 3600000,\n" + "    \"isSync\": true,\n"
        + "    \"transactionId\": \"0a119bb0-bcc8-4ac3-9802-f9aa4608220c\",\n"
        + "    \"status\": \"success\",\n" + "    \"response\": {\n"
        + "        \"response\": \"{\\\"AutomationOutputData\\\":[{\\\"AppName\\\":\\\"Microbot : CCSEncryptDecrypt\\\",\\\"CviewDictionary\\\":{\\\"errorMessage\\\":\\\"\\\"}},{\\\"AppName\\\":\\\"CCS\\\",\\\"CviewDictionary\\\":{}},{\\\"AppName\\\":\\\"Microbot : API_ResponsePayload\\\",\\\"CviewDictionary\\\":{\\\"ErrorDescription\\\":\\\"\\\",\\\"IsError\\\":\\\"False\\\",\\\"Status\\\":\\\"Awarding Action 'Pre-Award' to supplier 'Bathula Consulting' for itt_12216 completed successfully by venki.bathula@brickendon.com\\\"}}],\\\"HttpStatus\\\":\\\"\\\",\\\"ParentRequest\\\":{\\\"AutomationInputDictionary\\\":{\\\"Search.Username\\\":\\\"venki.bathula@brickendon.com\\\",\\\"Search.Password\\\":\\\"BFC9yX2TRFxMNa7f6vZLzg==\\\",\\\"Search.ITTCode\\\":\\\"itt_12216\\\",\\\"Search.SupplierName\\\":\\\"Bathula Consulting\\\",\\\"Search.AwardingAction\\\":\\\"Pre-Award\\\"},\\\"MaskedAutomationInputDictionary\\\":{\\\"Search.Username\\\":\\\"venki.bathula@brickendon.com\\\",\\\"Search.Password\\\":\\\"t2z0NJqgQvJslK8s91n+vt1ARBM4c1fiycXs0MmONN02gQ9C93ohFsUdSGx1eE2RTCZUczZ8ZHKKBN9jzMVsMA==\\\",\\\"Search.ITTCode\\\":\\\"itt_12216\\\",\\\"Search.SupplierName\\\":\\\"Bathula Consulting\\\",\\\"Search.AwardingAction\\\":\\\"Pre-Award\\\"},\\\"AnonymizedAutomationInputDictionary\\\":{\\\"Search.Username\\\":\\\"venki.bathula@brickendon.com\\\",\\\"Search.Password\\\":\\\"t2z0NJqgQvJslK8s91n+vt1ARBM4c1fiycXs0MmONN02gQ9C93ohFsUdSGx1eE2RTCZUczZ8ZHKKBN9jzMVsMA==\\\",\\\"Search.ITTCode\\\":\\\"itt_12216\\\",\\\"Search.SupplierName\\\":\\\"Bathula Consulting\\\",\\\"Search.AwardingAction\\\":\\\"Pre-Award\\\"},\\\"DataProtectionErrors\\\":{},\\\"ProcessName\\\":\\\"Awarding\\\",\\\"ProfileName\\\":\\\"ITTEvaluation\\\",\\\"RetryFlag\\\":false,\\\"Retry\\\":{\\\"NoOfTimesRetry\\\":0,\\\"RetryCoolOffInterval\\\":10000,\\\"PreviousRequestIds\\\":[]},\\\"APIVersion\\\":\\\"\\\",\\\"AppId\\\":\\\"\\\",\\\"CommandExecutionWindow\\\":\\\"\\\",\\\"CommandGenerationSource\\\":\\\"\\\",\\\"Country\\\":\\\"\\\",\\\"Instance\\\":\\\"\\\",\\\"PartnerId\\\":\\\"\\\",\\\"ReferenceCode\\\":\\\"\\\",\\\"Timestamp\\\":\\\"2022-03-23T16:40:38.875Z\\\",\\\"UserName\\\":\\\"testuser\\\",\\\"VID\\\":null,\\\"SENodeId\\\":null,\\\"RequestExpiration\\\":\\\"3600000\\\",\\\"wait_for_completion\\\":\\\"true\\\",\\\"Impersonation\\\":null,\\\"IfExternalVaultType\\\":false},\\\"ReferenceCode\\\":\\\"\\\",\\\"RequestExecutionTime\\\":\\\"00:00:17.7566188\\\",\\\"SEReferenceCode\\\":\\\"\\\",\\\"SpareParam1\\\":\\\"\\\",\\\"SpareParam2\\\":\\\"\\\",\\\"UserName\\\":\\\"testuser\\\",\\\"CommandResultEnum\\\":0,\\\"CommandResultDescription\\\":\\\"SearchSuccessful\\\",\\\"ErrorDetailObject\\\":[],\\\"SENodeId\\\":null,\\\"RequestReceivedTime\\\":\\\"2022-03-23T16:40:38.8968326Z\\\",\\\"RequestProcessedTime\\\":\\\"2022-03-23T16:40:56.6534514Z\\\",\\\"TransactionDescription\\\":\\\"SearchSuccessful\\\"}\"\n"
        + "    },\n" + "    \"error\": {}\n" + "}";

    inputBuilder.userName(PRINCIPAL).password(BUYER_PASSWORD).ittCode(EXTERNAL_EVENT_ID)
        .supplierName(JAGGAER_SUPPLIER_NAME).awardAction(PRE_AWARD);

    request.setProcessName(RPAProcessNameEnum.AWARDING.getValue())
        .setProfileName(rpaAPIConfig.getProfileName()).setSource(rpaAPIConfig.getSource())
        .setRetry(false).setSourceId(rpaAPIConfig.getSourceId()).setRequestTimeout(3600000)
        .setSync(true);

    request.setProcessInput(new ObjectMapper().writeValueAsString(inputBuilder.build()));

    RPAAPIResponse responseObject =
        new ObjectMapper().readValue(responseString, RPAAPIResponse.class);

    log.info("Test Request: {}", new ObjectMapper().writeValueAsString(request));
    var jaggerRPACredentials = new HashMap<String, String>();
    jaggerRPACredentials.put("username", rpaAPIConfig.getUserName());
    jaggerRPACredentials.put("password", rpaAPIConfig.getUserPwd());
    var uriTemplate = rpaAPIConfig.getAuthenticationUrl();
    var rfxResponse = prepareSupplierDetails();

    // Mock behaviours
    when(jaggaerService.getRfx(RFX_ID)).thenReturn(rfxResponse);
    when(retryableTendersDBDelegate
        .findOrganisationMappingByOrganisationIdIn(Set.of(SUPPLIER_ORG_ID_1)))
            .thenReturn(Set.of(ORG_MAPPING_1));
    when(webclientWrapper.postData(jaggerRPACredentials, String.class, rpaServiceWebClient,
        rpaAPIConfig.getTimeoutDuration(), uriTemplate)).thenReturn("token");
    when(webclientWrapper.postDataWithToken(request, RPAAPIResponse.class, rpaServiceWebClient,
        rpaAPIConfig.getTimeoutDuration(), rpaAPIConfig.getAccessUrl(), "token"))
            .thenReturn(responseObject);
    when(userProfileService.resolveBuyerUserBySSOUserLogin(PRINCIPAL)).thenReturn(JAGGAER_USER);
    when(validationService.validateProjectAndEventIds(PROC_PROJECT_ID, EVENT_OCID))
        .thenReturn(ProcurementEvent.builder().externalReferenceId(EXTERNAL_EVENT_ID)
            .externalEventId(RFX_ID).build());
    when(buyerDetailsRepo.findById(JAGGAER_USER_ID))
        .thenReturn(Optional.of(BuyerUserDetails.builder().userPassword(BUYER_PASSWORD).build()));

    // Invoke
    var supplierResponse = awardService.createOrUpdateAward(PRINCIPAL, PROC_PROJECT_ID, EVENT_OCID,
        AwardState.PRE_AWARD, award, null);

    // Assert
    assertNotNull(supplierResponse);
  }

  @Test
  void testEndEvaluation() throws JsonProcessingException {
    // Stub some objects
    var responseString = "{\n"
        + "    \"processInput\": \"{\\\"Search.Username\\\":\\\"venki.bathula@brickendon.com\\\",\\\"Search.Password\\\":\\\"BFC9yX2TRFxMNa7f6vZLzg==\\\",\\\"Search.ITTCode\\\":\\\"itt_12216\\\"}\",\n"
        + "    \"processName\": \"EndEvaluation\",\n" + "    \"profileName\": \"ITTEvaluation\",\n"
        + "    \"source\": \"Postman\",\n" + "    \"sourceId\": \"1\",\n"
        + "    \"retry\": false,\n" + "    \"retryConfigurations\": {\n"
        + "        \"previousRequestIds\": [],\n" + "        \"noOfTimesRetry\": 0,\n"
        + "        \"retryCoolOffInterval\": 10000\n" + "    },\n"
        + "    \"requestTimeout\": 3600000,\n" + "    \"isSync\": true,\n"
        + "    \"transactionId\": \"e6a70288-08e9-4e8c-84ce-20eaf1eababa\",\n"
        + "    \"status\": \"success\",\n" + "    \"response\": {\n"
        + "        \"response\": \"{\\\"AutomationOutputData\\\":[{\\\"AppName\\\":\\\"Microbot : CCSEncryptDecrypt\\\",\\\"CviewDictionary\\\":{\\\"errorMessage\\\":\\\"\\\"}},{\\\"AppName\\\":\\\"CCS\\\",\\\"CviewDictionary\\\":{}},{\\\"AppName\\\":\\\"Microbot : API_ResponsePayload\\\",\\\"CviewDictionary\\\":{\\\"ErrorDescription\\\":\\\"\\\",\\\"IsError\\\":\\\"False\\\",\\\"Status\\\":\\\"End Evaluation for itt_12216 completed successfully by venki.bathula@brickendon.com\\\"}}],\\\"HttpStatus\\\":\\\"\\\",\\\"ParentRequest\\\":{\\\"AutomationInputDictionary\\\":{\\\"Search.Username\\\":\\\"venki.bathula@brickendon.com\\\",\\\"Search.Password\\\":\\\"BFC9yX2TRFxMNa7f6vZLzg==\\\",\\\"Search.ITTCode\\\":\\\"itt_12216\\\"},\\\"MaskedAutomationInputDictionary\\\":{\\\"Search.Username\\\":\\\"venki.bathula@brickendon.com\\\",\\\"Search.Password\\\":\\\"t2z0NJqgQvJslK8s91n+vt1ARBM4c1fiycXs0MmONN02gQ9C93ohFsUdSGx1eE2RTCZUczZ8ZHKKBN9jzMVsMA==\\\",\\\"Search.ITTCode\\\":\\\"itt_12216\\\"},\\\"AnonymizedAutomationInputDictionary\\\":{\\\"Search.Username\\\":\\\"venki.bathula@brickendon.com\\\",\\\"Search.Password\\\":\\\"t2z0NJqgQvJslK8s91n+vt1ARBM4c1fiycXs0MmONN02gQ9C93ohFsUdSGx1eE2RTCZUczZ8ZHKKBN9jzMVsMA==\\\",\\\"Search.ITTCode\\\":\\\"itt_12216\\\"},\\\"DataProtectionErrors\\\":{},\\\"ProcessName\\\":\\\"EndEvaluation\\\",\\\"ProfileName\\\":\\\"ITTEvaluation\\\",\\\"RetryFlag\\\":false,\\\"Retry\\\":{\\\"NoOfTimesRetry\\\":0,\\\"RetryCoolOffInterval\\\":10000,\\\"PreviousRequestIds\\\":[]},\\\"APIVersion\\\":\\\"\\\",\\\"AppId\\\":\\\"\\\",\\\"CommandExecutionWindow\\\":\\\"\\\",\\\"CommandGenerationSource\\\":\\\"\\\",\\\"Country\\\":\\\"\\\",\\\"Instance\\\":\\\"\\\",\\\"PartnerId\\\":\\\"\\\",\\\"ReferenceCode\\\":\\\"\\\",\\\"Timestamp\\\":\\\"2022-03-23T16:39:03.505Z\\\",\\\"UserName\\\":\\\"testuser\\\",\\\"VID\\\":null,\\\"SENodeId\\\":null,\\\"RequestExpiration\\\":\\\"3600000\\\",\\\"wait_for_completion\\\":\\\"true\\\",\\\"Impersonation\\\":null,\\\"IfExternalVaultType\\\":false},\\\"ReferenceCode\\\":\\\"\\\",\\\"RequestExecutionTime\\\":\\\"00:00:13.1967600\\\",\\\"SEReferenceCode\\\":\\\"\\\",\\\"SpareParam1\\\":\\\"\\\",\\\"SpareParam2\\\":\\\"\\\",\\\"UserName\\\":\\\"testuser\\\",\\\"CommandResultEnum\\\":0,\\\"CommandResultDescription\\\":\\\"SearchSuccessful\\\",\\\"ErrorDetailObject\\\":[],\\\"SENodeId\\\":null,\\\"RequestReceivedTime\\\":\\\"2022-03-23T16:39:03.5390879Z\\\",\\\"RequestProcessedTime\\\":\\\"2022-03-23T16:39:16.7358479Z\\\",\\\"TransactionDescription\\\":\\\"SearchSuccessful\\\"}\"\n"
        + "    },\n" + "    \"error\": {}\n" + "}";

    inputBuilder.userName(PRINCIPAL).password(BUYER_PASSWORD).ittCode(EXTERNAL_EVENT_ID);
    request.setProcessName(RPAProcessNameEnum.END_EVALUATION.getValue())
        .setProfileName(rpaAPIConfig.getProfileName()).setSource(rpaAPIConfig.getSource())
        .setRetry(false).setSourceId(rpaAPIConfig.getSourceId()).setRequestTimeout(3600000)
        .setSync(true);
    request.setProcessInput(new ObjectMapper().writeValueAsString(inputBuilder.build()));

    RPAAPIResponse responseObject =
        new ObjectMapper().readValue(responseString, RPAAPIResponse.class);

    log.info("Test Request: {}", new ObjectMapper().writeValueAsString(request));
    var jaggerRPACredentials = new HashMap<String, String>();
    jaggerRPACredentials.put("username", rpaAPIConfig.getUserName());
    jaggerRPACredentials.put("password", rpaAPIConfig.getUserPwd());
    var uriTemplate = rpaAPIConfig.getAuthenticationUrl();

    // Mock behaviours
    when(webclientWrapper.postData(jaggerRPACredentials, String.class, rpaServiceWebClient,
        rpaAPIConfig.getTimeoutDuration(), uriTemplate)).thenReturn("token");
    when(webclientWrapper.postDataWithToken(request, RPAAPIResponse.class, rpaServiceWebClient,
        rpaAPIConfig.getTimeoutDuration(), rpaAPIConfig.getAccessUrl(), "token"))
            .thenReturn(responseObject);
    when(userProfileService.resolveBuyerUserBySSOUserLogin(PRINCIPAL)).thenReturn(JAGGAER_USER);
    when(validationService.validateProjectAndEventIds(PROC_PROJECT_ID, EVENT_OCID))
        .thenReturn(ProcurementEvent.builder().externalReferenceId(EXTERNAL_EVENT_ID)
            .externalEventId(RFX_ID).build());
    when(buyerDetailsRepo.findById(JAGGAER_USER_ID))
        .thenReturn(Optional.of(BuyerUserDetails.builder().userPassword(BUYER_PASSWORD).build()));

    // Invoke
    var supplierResponse =
        awardService.callEndEvaluation(PRINCIPAL, BUYER_PASSWORD, EXTERNAL_EVENT_ID);

    // Assert
    assertNotNull(supplierResponse);
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

}
