package uk.gov.crowncommercial.dts.scale.cat.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.verify;
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
import uk.gov.crowncommercial.dts.scale.cat.model.generated.ScoreAndCommentNonOCDS;
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
@SpringBootTest(classes = {SupplierService.class, RPAGenericService.class},
    webEnvironment = WebEnvironment.NONE)
@Slf4j
@EnableConfigurationProperties({RPAAPIConfig.class})
@ContextConfiguration(classes = {ObjectMapper.class})
class SupplierServiceTest {

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

  private static final Double SCORE_1 = 90.02;
  private static final Double SCORE_2 = 70.45;
  private static final String COMMENT_1 = "comment-1";
  private static final String COMMENT_2 = "comment-2";

  private static final String RFX_ID = "rfq_0001";
  private static final Integer JAGGAER_SUPPLIER_ID = 2345678;
  private static final String JAGGAER_SUPPLIER_NAME = "Bathula Consulting";

  private static final Integer JAGGAER_SUPPLIER_ID_1 = 8765432;
  private static final String JAGGAER_SUPPLIER_NAME_1 = "Doshi Industries";

  static final ProcurementProject project = ProcurementProject.builder().build();

  private static final Optional<SubUser> JAGGAER_USER = Optional
      .of(SubUser.builder().userId(JAGGAER_USER_ID).email(PRINCIPAL).name(BUYER_USER_NAME).build());

  @MockBean
  private AgreementsService agreementsService;

  @MockBean
  private RetryableTendersDBDelegate retryableTendersDBDelegate;

  @Autowired
  private SupplierService supplierService;

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
  private final RPAProcessInputBuilder inputBuilder = RPAProcessInput.builder();

  @BeforeAll
  static void beforeClass() {
    ORG_MAPPING_1.setOrganisationId(SUPPLIER_ORG_ID_1);
    ORG_MAPPING_1.setExternalOrganisationId(EXT_ORG_ID_1);
    ORG_MAPPING_2.setOrganisationId(SUPPLIER_ORG_ID_2);
    ORG_MAPPING_2.setExternalOrganisationId(EXT_ORG_ID_2);
  }

  @Test
  void testResolveSuppliers() throws Exception {
    when(agreementsService.getLotSuppliers(AGREEMENT_NUMBER, LOT_NUMBER))
        .thenReturn(Set.of(LOT_SUPPLIER_1, LOT_SUPPLIER_2));

    when(retryableTendersDBDelegate
        .findOrganisationMappingByOrganisationIdIn(Set.of(SUPPLIER_ORG_ID_1, SUPPLIER_ORG_ID_2)))
            .thenReturn(Set.of(ORG_MAPPING_1, ORG_MAPPING_2));

    var suppliers = supplierService.resolveSuppliers(AGREEMENT_NUMBER, LOT_NUMBER);

    assertEquals(Set.of(EXT_SUPPLIER_1, EXT_SUPPLIER_2), Set.copyOf(suppliers));

    verify(agreementsService).getLotSuppliers(AGREEMENT_NUMBER, LOT_NUMBER);
    verify(retryableTendersDBDelegate).findOrganisationMappingByOrganisationIdIn(anySet());
  }

  @Test
  void testResolveSuppliersNonFoundInAS() throws Exception {
    when(agreementsService.getLotSuppliers(AGREEMENT_NUMBER, LOT_NUMBER)).thenReturn(Set.of());

    var suppliers = supplierService.resolveSuppliers(AGREEMENT_NUMBER, LOT_NUMBER);

    assertEquals(Set.of(), Set.copyOf(suppliers));

    verify(agreementsService).getLotSuppliers(AGREEMENT_NUMBER, LOT_NUMBER);
  }

  @Test
  void testResolveSuppliersNoOrgMappings() throws Exception {
    when(agreementsService.getLotSuppliers(AGREEMENT_NUMBER, LOT_NUMBER))
        .thenReturn(Set.of(LOT_SUPPLIER_1, LOT_SUPPLIER_2));

    when(retryableTendersDBDelegate
        .findOrganisationMappingByOrganisationIdIn(Set.of(SUPPLIER_ORG_ID_1, SUPPLIER_ORG_ID_2)))
            .thenReturn(Set.of());

    var suppliers = supplierService.resolveSuppliers(AGREEMENT_NUMBER, LOT_NUMBER);

    assertEquals(Set.of(), Set.copyOf(suppliers));

    verify(agreementsService).getLotSuppliers(AGREEMENT_NUMBER, LOT_NUMBER);
    verify(retryableTendersDBDelegate).findOrganisationMappingByOrganisationIdIn(anySet());
  }

  @Test
  void testUpdateSingleSupplierScoreAndComment() throws JsonProcessingException {
    // Stub some objects
    var scoreAndComments = List.of(new ScoreAndCommentNonOCDS().organisationId(SUPPLIER_ORG_ID_1)
        .comment(COMMENT_1).score(90.0));

    var responseString = "{\n"
        + "    \"processInput\": \"{\\\"Search.Username\\\":\\\"venki.bathula@brickendon.com\\\",\\\"Search.Password\\\":\\\"VenDol@3211\\\",\\\"Search.ITTCode\\\":\\\"itt_8673\\\",\\\"Search.SupplierName\\\":\\\"Bathula Consulting\\\",\\\"Search.Score\\\":\\\"45.9\\\",\\\"Search.Comment\\\":\\\"Venki Comment\\\"}\",\n"
        + "    \"processName\": \"AssignScore\",\n" + "    \"profileName\": \"ITTEvaluation\",\n"
        + "    \"source\": \"Postman\",\n" + "    \"sourceId\": \"1\",\n"
        + "    \"retry\": false,\n" + "    \"retryConfigurations\": {\n"
        + "        \"previousRequestIds\": [],\n" + "        \"noOfTimesRetry\": 0,\n"
        + "        \"retryCoolOffInterval\": 10000\n" + "    },\n"
        + "    \"requestTimeout\": 3600000,\n" + "    \"isSync\": true,\n"
        + "    \"transactionId\": \"e5f4dde5-7329-42ca-b1a9-797b819d8931\",\n"
        + "    \"status\": \"success\",\n" + "    \"response\": {\n"
        + "        \"response\": \"{\\\"AutomationOutputData\\\":[{\\\"AppName\\\":\\\"CCS\\\",\\\"CviewDictionary\\\":{\\\"isTrue\\\":\\\"true\\\"}},{\\\"AppName\\\":\\\"Microbot : API_ResponsePayload\\\",\\\"CviewDictionary\\\":{\\\"ErrorDescription\\\":\\\"\\\",\\\"IsError\\\":\\\"False\\\",\\\"Status\\\":\\\"Assign Score for itt_8673 completed by venki.bathula@brickendon.com. Details-\\\\r\\\\nSupplier:Bathula Consulting | Score:45.9 | Comment:Venki Comment | Status:Success\\\"}}],\\\"HttpStatus\\\":\\\"\\\",\\\"ParentRequest\\\":{\\\"AutomationInputDictionary\\\":{\\\"Search.Username\\\":\\\"venki.bathula@brickendon.com\\\",\\\"Search.Password\\\":\\\"VenDol@3211\\\",\\\"Search.ITTCode\\\":\\\"itt_8673\\\",\\\"Search.SupplierName\\\":\\\"Bathula Consulting\\\",\\\"Search.Score\\\":\\\"45.9\\\",\\\"Search.Comment\\\":\\\"Venki Comment\\\"},\\\"MaskedAutomationInputDictionary\\\":{\\\"Search.Username\\\":\\\"venki.bathula@brickendon.com\\\",\\\"Search.Password\\\":\\\"HPSW0Mx6ZRMXo/ok+T/2u/+BY5gjR1ajS+LgqtyPSRYz/kXgKywqh4AeYduOpypcshDH82OEF6osmnj0b3gNYg==\\\",\\\"Search.ITTCode\\\":\\\"itt_8673\\\",\\\"Search.SupplierName\\\":\\\"Bathula Consulting\\\",\\\"Search.Score\\\":\\\"45.9\\\",\\\"Search.Comment\\\":\\\"Venki Comment\\\"},\\\"AnonymizedAutomationInputDictionary\\\":{\\\"Search.Username\\\":\\\"venki.bathula@brickendon.com\\\",\\\"Search.Password\\\":\\\"HPSW0Mx6ZRMXo/ok+T/2u/+BY5gjR1ajS+LgqtyPSRYz/kXgKywqh4AeYduOpypcshDH82OEF6osmnj0b3gNYg==\\\",\\\"Search.ITTCode\\\":\\\"itt_8673\\\",\\\"Search.SupplierName\\\":\\\"Bathula Consulting\\\",\\\"Search.Score\\\":\\\"45.9\\\",\\\"Search.Comment\\\":\\\"Venki Comment\\\"},\\\"DataProtectionErrors\\\":{},\\\"ProcessName\\\":\\\"AssignScore\\\",\\\"ProfileName\\\":\\\"ITTEvaluation\\\",\\\"RetryFlag\\\":false,\\\"Retry\\\":{\\\"NoOfTimesRetry\\\":0,\\\"RetryCoolOffInterval\\\":10000,\\\"PreviousRequestIds\\\":[]},\\\"APIVersion\\\":\\\"\\\",\\\"AppId\\\":\\\"\\\",\\\"CommandExecutionWindow\\\":\\\"\\\",\\\"CommandGenerationSource\\\":\\\"\\\",\\\"Country\\\":\\\"\\\",\\\"Instance\\\":\\\"\\\",\\\"PartnerId\\\":\\\"\\\",\\\"ReferenceCode\\\":\\\"\\\",\\\"Timestamp\\\":\\\"2022-03-11T15:38:00.224Z\\\",\\\"UserName\\\":\\\"testuser\\\",\\\"VID\\\":null,\\\"SENodeId\\\":null,\\\"RequestExpiration\\\":\\\"3600000\\\",\\\"wait_for_completion\\\":\\\"true\\\",\\\"Impersonation\\\":null,\\\"IfExternalVaultType\\\":false},\\\"ReferenceCode\\\":\\\"\\\",\\\"RequestExecutionTime\\\":\\\"00:00:14.8731423\\\",\\\"SEReferenceCode\\\":\\\"\\\",\\\"SpareParam1\\\":\\\"\\\",\\\"SpareParam2\\\":\\\"\\\",\\\"UserName\\\":\\\"testuser\\\",\\\"CommandResultEnum\\\":0,\\\"CommandResultDescription\\\":\\\"SearchSuccessful\\\",\\\"ErrorDetailObject\\\":[],\\\"SENodeId\\\":null,\\\"RequestReceivedTime\\\":\\\"2022-03-11T15:38:02.3610332Z\\\",\\\"RequestProcessedTime\\\":\\\"2022-03-11T15:38:17.2341755Z\\\",\\\"TransactionDescription\\\":\\\"SearchSuccessful\\\"}\"\n"
        + "    },\n" + "    \"error\": {}\n" + "}";

    inputBuilder.userName(PRINCIPAL).password(BUYER_PASSWORD).ittCode(EXTERNAL_EVENT_ID)
        .score("90.0").comment(COMMENT_1).supplierName(JAGGAER_SUPPLIER_NAME);

    request.setProcessName(RPAProcessNameEnum.ASSIGN_SCORE.getValue())
        .setProfileName(rpaAPIConfig.getProfileName()).setSource(rpaAPIConfig.getSource())
        .setRetry(false).setSourceId(rpaAPIConfig.getSourceId()).setRequestTimeout(3600000)
        .setSync(true);

    request.setProcessInput(new ObjectMapper().writeValueAsString(inputBuilder.build()));

    var responseObject =
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
    when(userProfileService.resolveBuyerUserProfile(PRINCIPAL)).thenReturn(JAGGAER_USER);
    when(validationService.validateProjectAndEventIds(PROC_PROJECT_ID, EVENT_OCID))
        .thenReturn(ProcurementEvent.builder().externalReferenceId(EXTERNAL_EVENT_ID)
            .externalEventId(RFX_ID).build());
    when(buyerDetailsRepo.findById(JAGGAER_USER_ID))
        .thenReturn(Optional.of(BuyerUserDetails.builder().userPassword(BUYER_PASSWORD).build()));

    // Invoke
    var supplierResponse = supplierService.updateSupplierScoreAndComment(PRINCIPAL, PROC_PROJECT_ID,
        EVENT_OCID, scoreAndComments);

    // Assert
    assertNotNull(supplierResponse);
  }

  @Test
  void testUpdateMultipleSupplierScoreAndComment() throws JsonProcessingException {
    // Stub some objects
    var scoreAndComments = List.of(
        new ScoreAndCommentNonOCDS().organisationId(SUPPLIER_ORG_ID_1).comment(COMMENT_1)
            .score(SCORE_1),
        new ScoreAndCommentNonOCDS().organisationId(SUPPLIER_ORG_ID_2).comment(COMMENT_2)
            .score(SCORE_2));

    var responseString = "{\n"
        + "    \"processInput\": \"{\\\"Search.Username\\\":\\\"venki.bathula@brickendon.com\\\",\\\"Search.Password\\\":\\\"VenDol@3211\\\",\\\"Search.ITTCode\\\":\\\"itt_8673\\\",\\\"Search.SupplierName\\\":\\\"Bathula Consulting\\\",\\\"Search.Score\\\":\\\"45.9\\\",\\\"Search.Comment\\\":\\\"Venki Comment\\\"}\",\n"
        + "    \"processName\": \"AssignScore\",\n" + "    \"profileName\": \"ITTEvaluation\",\n"
        + "    \"source\": \"Postman\",\n" + "    \"sourceId\": \"1\",\n"
        + "    \"retry\": false,\n" + "    \"retryConfigurations\": {\n"
        + "        \"previousRequestIds\": [],\n" + "        \"noOfTimesRetry\": 0,\n"
        + "        \"retryCoolOffInterval\": 10000\n" + "    },\n"
        + "    \"requestTimeout\": 3600000,\n" + "    \"isSync\": true,\n"
        + "    \"transactionId\": \"e5f4dde5-7329-42ca-b1a9-797b819d8931\",\n"
        + "    \"status\": \"success\",\n" + "    \"response\": {\n"
        + "        \"response\": \"{\\\"AutomationOutputData\\\":[{\\\"AppName\\\":\\\"CCS\\\",\\\"CviewDictionary\\\":{\\\"isTrue\\\":\\\"true\\\"}},{\\\"AppName\\\":\\\"Microbot : API_ResponsePayload\\\",\\\"CviewDictionary\\\":{\\\"ErrorDescription\\\":\\\"\\\",\\\"IsError\\\":\\\"False\\\",\\\"Status\\\":\\\"Assign Score for itt_8673 completed by venki.bathula@brickendon.com. Details-\\\\r\\\\nSupplier:Bathula Consulting | Score:45.9 | Comment:Venki Comment | Status:Success\\\"}}],\\\"HttpStatus\\\":\\\"\\\",\\\"ParentRequest\\\":{\\\"AutomationInputDictionary\\\":{\\\"Search.Username\\\":\\\"venki.bathula@brickendon.com\\\",\\\"Search.Password\\\":\\\"VenDol@3211\\\",\\\"Search.ITTCode\\\":\\\"itt_8673\\\",\\\"Search.SupplierName\\\":\\\"Bathula Consulting\\\",\\\"Search.Score\\\":\\\"45.9\\\",\\\"Search.Comment\\\":\\\"Venki Comment\\\"},\\\"MaskedAutomationInputDictionary\\\":{\\\"Search.Username\\\":\\\"venki.bathula@brickendon.com\\\",\\\"Search.Password\\\":\\\"HPSW0Mx6ZRMXo/ok+T/2u/+BY5gjR1ajS+LgqtyPSRYz/kXgKywqh4AeYduOpypcshDH82OEF6osmnj0b3gNYg==\\\",\\\"Search.ITTCode\\\":\\\"itt_8673\\\",\\\"Search.SupplierName\\\":\\\"Bathula Consulting\\\",\\\"Search.Score\\\":\\\"45.9\\\",\\\"Search.Comment\\\":\\\"Venki Comment\\\"},\\\"AnonymizedAutomationInputDictionary\\\":{\\\"Search.Username\\\":\\\"venki.bathula@brickendon.com\\\",\\\"Search.Password\\\":\\\"HPSW0Mx6ZRMXo/ok+T/2u/+BY5gjR1ajS+LgqtyPSRYz/kXgKywqh4AeYduOpypcshDH82OEF6osmnj0b3gNYg==\\\",\\\"Search.ITTCode\\\":\\\"itt_8673\\\",\\\"Search.SupplierName\\\":\\\"Bathula Consulting\\\",\\\"Search.Score\\\":\\\"45.9\\\",\\\"Search.Comment\\\":\\\"Venki Comment\\\"},\\\"DataProtectionErrors\\\":{},\\\"ProcessName\\\":\\\"AssignScore\\\",\\\"ProfileName\\\":\\\"ITTEvaluation\\\",\\\"RetryFlag\\\":false,\\\"Retry\\\":{\\\"NoOfTimesRetry\\\":0,\\\"RetryCoolOffInterval\\\":10000,\\\"PreviousRequestIds\\\":[]},\\\"APIVersion\\\":\\\"\\\",\\\"AppId\\\":\\\"\\\",\\\"CommandExecutionWindow\\\":\\\"\\\",\\\"CommandGenerationSource\\\":\\\"\\\",\\\"Country\\\":\\\"\\\",\\\"Instance\\\":\\\"\\\",\\\"PartnerId\\\":\\\"\\\",\\\"ReferenceCode\\\":\\\"\\\",\\\"Timestamp\\\":\\\"2022-03-11T15:38:00.224Z\\\",\\\"UserName\\\":\\\"testuser\\\",\\\"VID\\\":null,\\\"SENodeId\\\":null,\\\"RequestExpiration\\\":\\\"3600000\\\",\\\"wait_for_completion\\\":\\\"true\\\",\\\"Impersonation\\\":null,\\\"IfExternalVaultType\\\":false},\\\"ReferenceCode\\\":\\\"\\\",\\\"RequestExecutionTime\\\":\\\"00:00:14.8731423\\\",\\\"SEReferenceCode\\\":\\\"\\\",\\\"SpareParam1\\\":\\\"\\\",\\\"SpareParam2\\\":\\\"\\\",\\\"UserName\\\":\\\"testuser\\\",\\\"CommandResultEnum\\\":0,\\\"CommandResultDescription\\\":\\\"SearchSuccessful\\\",\\\"ErrorDetailObject\\\":[],\\\"SENodeId\\\":null,\\\"RequestReceivedTime\\\":\\\"2022-03-11T15:38:02.3610332Z\\\",\\\"RequestProcessedTime\\\":\\\"2022-03-11T15:38:17.2341755Z\\\",\\\"TransactionDescription\\\":\\\"SearchSuccessful\\\"}\"\n"
        + "    },\n" + "    \"error\": {}\n" + "}";

    inputBuilder.userName(PRINCIPAL).password(BUYER_PASSWORD).ittCode(EXTERNAL_EVENT_ID)
        .score(SCORE_1.toString() + "~|" + SCORE_2.toString()).comment(COMMENT_1 + "~|" + COMMENT_2)
        .supplierName(JAGGAER_SUPPLIER_NAME + "~|" + JAGGAER_SUPPLIER_NAME_1);

    request.setProcessName(RPAProcessNameEnum.ASSIGN_SCORE.getValue())
        .setProfileName(rpaAPIConfig.getProfileName()).setSource(rpaAPIConfig.getSource())
        .setRetry(false).setSourceId(rpaAPIConfig.getSourceId()).setRequestTimeout(3600000)
        .setSync(true);

    request.setProcessInput(new ObjectMapper().writeValueAsString(inputBuilder.build()));

    var responseObject =
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
        .findOrganisationMappingByOrganisationIdIn(Set.of(SUPPLIER_ORG_ID_1, SUPPLIER_ORG_ID_2)))
            .thenReturn(Set.of(ORG_MAPPING_1, ORG_MAPPING_2));
    when(webclientWrapper.postData(jaggerRPACredentials, String.class, rpaServiceWebClient,
        rpaAPIConfig.getTimeoutDuration(), uriTemplate)).thenReturn("token");
    when(webclientWrapper.postDataWithToken(request, RPAAPIResponse.class, rpaServiceWebClient,
        rpaAPIConfig.getTimeoutDuration(), rpaAPIConfig.getAccessUrl(), "token"))
            .thenReturn(responseObject);
    when(userProfileService.resolveBuyerUserProfile(PRINCIPAL)).thenReturn(JAGGAER_USER);
    when(validationService.validateProjectAndEventIds(PROC_PROJECT_ID, EVENT_OCID))
        .thenReturn(ProcurementEvent.builder().externalReferenceId(EXTERNAL_EVENT_ID)
            .externalEventId(RFX_ID).build());
    when(buyerDetailsRepo.findById(JAGGAER_USER_ID))
        .thenReturn(Optional.of(BuyerUserDetails.builder().userPassword(BUYER_PASSWORD).build()));

    // Invoke
    var supplierResponse = supplierService.updateSupplierScoreAndComment(PRINCIPAL, PROC_PROJECT_ID,
        EVENT_OCID, scoreAndComments);

    // Assert
    assertNotNull(supplierResponse);

  }

  @Test
  void testCallOpenEnvelope() throws JsonProcessingException {
    // Stub some objects
    var responseString = "{\n"
        + "    \"processInput\": \"{\\\"Search.Username\\\":\\\"venki.bathula@brickendon.com\\\",\\\"Search.Password\\\":\\\"VenDol@3211\\\",\\\"Search.ITTCode\\\":\\\"itt_8673\\\",\\\"Search.SupplierName\\\":\\\"Bathula Consulting\\\",\\\"Search.Score\\\":\\\"45.9\\\",\\\"Search.Comment\\\":\\\"Venki Comment\\\"}\",\n"
        + "    \"processName\": \"AssignScore\",\n" + "    \"profileName\": \"ITTEvaluation\",\n"
        + "    \"source\": \"Postman\",\n" + "    \"sourceId\": \"1\",\n"
        + "    \"retry\": false,\n" + "    \"retryConfigurations\": {\n"
        + "        \"previousRequestIds\": [],\n" + "        \"noOfTimesRetry\": 0,\n"
        + "        \"retryCoolOffInterval\": 10000\n" + "    },\n"
        + "    \"requestTimeout\": 3600000,\n" + "    \"isSync\": true,\n"
        + "    \"transactionId\": \"e5f4dde5-7329-42ca-b1a9-797b819d8931\",\n"
        + "    \"status\": \"success\",\n" + "    \"response\": {\n"
        + "        \"response\": \"{\\\"AutomationOutputData\\\":[{\\\"AppName\\\":\\\"CCS\\\",\\\"CviewDictionary\\\":{\\\"isTrue\\\":\\\"true\\\"}},{\\\"AppName\\\":\\\"Microbot : API_ResponsePayload\\\",\\\"CviewDictionary\\\":{\\\"ErrorDescription\\\":\\\"\\\",\\\"IsError\\\":\\\"False\\\",\\\"Status\\\":\\\"Assign Score for itt_8673 completed by venki.bathula@brickendon.com. Details-\\\\r\\\\nSupplier:Bathula Consulting | Score:45.9 | Comment:Venki Comment | Status:Success\\\"}}],\\\"HttpStatus\\\":\\\"\\\",\\\"ParentRequest\\\":{\\\"AutomationInputDictionary\\\":{\\\"Search.Username\\\":\\\"venki.bathula@brickendon.com\\\",\\\"Search.Password\\\":\\\"VenDol@3211\\\",\\\"Search.ITTCode\\\":\\\"itt_8673\\\",\\\"Search.SupplierName\\\":\\\"Bathula Consulting\\\",\\\"Search.Score\\\":\\\"45.9\\\",\\\"Search.Comment\\\":\\\"Venki Comment\\\"},\\\"MaskedAutomationInputDictionary\\\":{\\\"Search.Username\\\":\\\"venki.bathula@brickendon.com\\\",\\\"Search.Password\\\":\\\"HPSW0Mx6ZRMXo/ok+T/2u/+BY5gjR1ajS+LgqtyPSRYz/kXgKywqh4AeYduOpypcshDH82OEF6osmnj0b3gNYg==\\\",\\\"Search.ITTCode\\\":\\\"itt_8673\\\",\\\"Search.SupplierName\\\":\\\"Bathula Consulting\\\",\\\"Search.Score\\\":\\\"45.9\\\",\\\"Search.Comment\\\":\\\"Venki Comment\\\"},\\\"AnonymizedAutomationInputDictionary\\\":{\\\"Search.Username\\\":\\\"venki.bathula@brickendon.com\\\",\\\"Search.Password\\\":\\\"HPSW0Mx6ZRMXo/ok+T/2u/+BY5gjR1ajS+LgqtyPSRYz/kXgKywqh4AeYduOpypcshDH82OEF6osmnj0b3gNYg==\\\",\\\"Search.ITTCode\\\":\\\"itt_8673\\\",\\\"Search.SupplierName\\\":\\\"Bathula Consulting\\\",\\\"Search.Score\\\":\\\"45.9\\\",\\\"Search.Comment\\\":\\\"Venki Comment\\\"},\\\"DataProtectionErrors\\\":{},\\\"ProcessName\\\":\\\"AssignScore\\\",\\\"ProfileName\\\":\\\"ITTEvaluation\\\",\\\"RetryFlag\\\":false,\\\"Retry\\\":{\\\"NoOfTimesRetry\\\":0,\\\"RetryCoolOffInterval\\\":10000,\\\"PreviousRequestIds\\\":[]},\\\"APIVersion\\\":\\\"\\\",\\\"AppId\\\":\\\"\\\",\\\"CommandExecutionWindow\\\":\\\"\\\",\\\"CommandGenerationSource\\\":\\\"\\\",\\\"Country\\\":\\\"\\\",\\\"Instance\\\":\\\"\\\",\\\"PartnerId\\\":\\\"\\\",\\\"ReferenceCode\\\":\\\"\\\",\\\"Timestamp\\\":\\\"2022-03-11T15:38:00.224Z\\\",\\\"UserName\\\":\\\"testuser\\\",\\\"VID\\\":null,\\\"SENodeId\\\":null,\\\"RequestExpiration\\\":\\\"3600000\\\",\\\"wait_for_completion\\\":\\\"true\\\",\\\"Impersonation\\\":null,\\\"IfExternalVaultType\\\":false},\\\"ReferenceCode\\\":\\\"\\\",\\\"RequestExecutionTime\\\":\\\"00:00:14.8731423\\\",\\\"SEReferenceCode\\\":\\\"\\\",\\\"SpareParam1\\\":\\\"\\\",\\\"SpareParam2\\\":\\\"\\\",\\\"UserName\\\":\\\"testuser\\\",\\\"CommandResultEnum\\\":0,\\\"CommandResultDescription\\\":\\\"SearchSuccessful\\\",\\\"ErrorDetailObject\\\":[],\\\"SENodeId\\\":null,\\\"RequestReceivedTime\\\":\\\"2022-03-11T15:38:02.3610332Z\\\",\\\"RequestProcessedTime\\\":\\\"2022-03-11T15:38:17.2341755Z\\\",\\\"TransactionDescription\\\":\\\"SearchSuccessful\\\"}\"\n"
        + "    },\n" + "    \"error\": {}\n" + "}";

    var scoreBuilder = RPAProcessInput.builder().userName(PRINCIPAL).password(BUYER_PASSWORD)
        .ittCode(EXTERNAL_EVENT_ID).score(SCORE_1.toString() + "~|" + SCORE_2.toString())
        .comment(COMMENT_1 + "~|" + COMMENT_2)
        .supplierName(JAGGAER_SUPPLIER_NAME + "~|" + JAGGAER_SUPPLIER_NAME_1);

    var scoreData = new RPAGenericData().setProcessName(RPAProcessNameEnum.ASSIGN_SCORE.getValue())
        .setProfileName(rpaAPIConfig.getProfileName()).setSource(rpaAPIConfig.getSource())
        .setRetry(false).setSourceId(rpaAPIConfig.getSourceId()).setRequestTimeout(3600000)
        .setSync(true);

    scoreData.setProcessInput(new ObjectMapper().writeValueAsString(scoreBuilder.build()));

    inputBuilder.userName(PRINCIPAL).password(BUYER_PASSWORD).ittCode(EXTERNAL_EVENT_ID);

    request.setProcessName(RPAProcessNameEnum.OPEN_ENVELOPE.getValue())
        .setProfileName(rpaAPIConfig.getProfileName()).setSource(rpaAPIConfig.getSource())
        .setRetry(false).setSourceId(rpaAPIConfig.getSourceId()).setRequestTimeout(3600000)
        .setSync(true);

    request.setProcessInput(new ObjectMapper().writeValueAsString(inputBuilder.build()));

    var responseObject =
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
        .findOrganisationMappingByOrganisationIdIn(Set.of(SUPPLIER_ORG_ID_1, SUPPLIER_ORG_ID_2)))
            .thenReturn(Set.of(ORG_MAPPING_1, ORG_MAPPING_2));
    when(webclientWrapper.postData(jaggerRPACredentials, String.class, rpaServiceWebClient,
        rpaAPIConfig.getTimeoutDuration(), uriTemplate)).thenReturn("token");
    when(webclientWrapper.postDataWithToken(scoreData, RPAAPIResponse.class, rpaServiceWebClient,
        rpaAPIConfig.getTimeoutDuration(), rpaAPIConfig.getAccessUrl(), "token"))
            .thenReturn(responseObject);
    when(webclientWrapper.postDataWithToken(request, RPAAPIResponse.class, rpaServiceWebClient,
        rpaAPIConfig.getTimeoutDuration(), rpaAPIConfig.getAccessUrl(), "token"))
            .thenReturn(responseObject);
    when(userProfileService.resolveBuyerUserProfile(PRINCIPAL)).thenReturn(JAGGAER_USER);
    when(validationService.validateProjectAndEventIds(PROC_PROJECT_ID, EVENT_OCID))
        .thenReturn(ProcurementEvent.builder().externalReferenceId(EXTERNAL_EVENT_ID)
            .externalEventId(RFX_ID).build());

    // Invoke
    var supplierResponse = supplierService.callOpenEnvelopeAndUpdateSupplier(PRINCIPAL,
        BUYER_PASSWORD, EXTERNAL_EVENT_ID, scoreBuilder.build());

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
