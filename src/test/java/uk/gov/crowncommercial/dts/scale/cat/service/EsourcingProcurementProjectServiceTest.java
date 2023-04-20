package uk.gov.crowncommercial.dts.scale.cat.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.crowncommercial.dts.scale.cat.config.Constants.ERR_MSG_JAGGAER_USER_NOT_FOUND;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.reactive.function.client.WebClient;

import uk.gov.crowncommercial.dts.scale.cat.auth.Authorities;
import uk.gov.crowncommercial.dts.scale.cat.auth.apikey.ApiKeyDetails;
import uk.gov.crowncommercial.dts.scale.cat.auth.apikey.ApiKeyDetailsProvider;
import uk.gov.crowncommercial.dts.scale.cat.config.AgreementsServiceAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.config.ApplicationFlagsConfig;
import uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.config.OcdsConfig;
import uk.gov.crowncommercial.dts.scale.cat.exception.AuthorisationFailureException;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.OrganisationMapping;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementProject;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.AgreementDetails;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.SalesforceProjectTender;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.SalesforceProjectTender200Response;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.SalesforceRfx;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.AdditionalInfo;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.AdditionalInfoValue;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.AdditionalInfoValues;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.BuyerCompany;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.CreateUpdateProject;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.CreateUpdateProjectResponse;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.CreateUpdateRfx;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.CreateUpdateRfxResponse;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.OperationCode;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.OwnerUser;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.ProjectOwner;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.Rfx;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.RfxAdditionalInfoList;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.RfxSetting;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.RfxTemplateMapping;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.SubUsers;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.SubUsers.SubUser;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;
import uk.gov.crowncommercial.dts.scale.cat.repo.readonly.RfxTemplateMappingRepo;
import uk.gov.crowncommercial.dts.scale.cat.utils.TendersAPIModelUtils;

/**
 * Service layer tests
 */
@SpringBootTest(
    classes = {ProcurementProjectService.class, JaggaerAPIConfig.class, TendersAPIModelUtils.class,
        ModelMapper.class, JaggaerService.class, ApplicationFlagsConfig.class,EventTransitionService.class},
    webEnvironment = WebEnvironment.NONE)
@EnableConfigurationProperties(JaggaerAPIConfig.class)
@ActiveProfiles("test")
class EsourcingProcurementProjectServiceTest {

  private static final String PRINCIPAL = "peter.simpson@roweit.co.uk";
  private static final String SHORT_DESCRIPTION = "Project for Provision of Face Masks";
  private static final String CA_NUMBER = "RM12345";
  private static final String LOT_NUMBER = "Lot 209";
  private static final String TEMPLATE_REFERENCE_CODE="project_609";
  private static final String RFX_ID = "1";
  private static final String RFX_REFERENCE_CODE = "itt_9899";
  private static final String TENDER_CODE = "tender_0001";
  private static final String TENDER_REF_CODE = "project_0001";
  private static final String PROJ_NAME = CA_NUMBER + '-' + LOT_NUMBER;
  private static final Integer PROC_PROJECT_ID = 1;
  private static final String EVENT_ID = "ocds-pfhb7i-1";
  private static final String PROCUREMENT_ROUTE = "Open Market";
  private static final String OWNER_USER_LOGIN = "peter.simpson@roweit.co.uk";
  private static final String BUYER_USER_NAME = "Peter Simpson";
  private static final String JAGGAER_USER_ID = "1";
  private static final Integer RFI_FLAG = 0;
  private static final String VALUE = "1234";
  private static final String RFX_TYPE = "STANDARD_ITT";
  private static final String QUAL_ENV_STATUS = "0";
  private static final String TECH_ENV_STATUS = "1";
  private static final String COMM_ENV_STATUS = "1";
  private static final String VIS_EG_COMMENTS = "1";
  private static final String RANKING_STRATEGY = "BEST_TECHNICAL_SCORE";
  private static final String PUBLISH_DATE = "2021-01-28T12:00:00.000+00:00";
  private static final String CLOSE_DATE = "2021-02-27T12:00:00.000+00:00";
  private static final String ADDITIONAL_INFO_FRAMEWORK_NAME = "Framework Name";
  private static final String ADDITIONAL_INFO_LOT_NUMBER = "Lot Number";
  private static final String ADDITIONAL_INFO_LOCALE = "en_GB";
  private static final String ADDITIONAL_INFO_PROCUREMENT_ROUTE = "Procurement Route";  
  private static final String ADDITIONAL_INFO_PROCUREMENT_ROUTE_TYPE = "5";  
  private static final String JAGGAER_BUYER_COMPANY_ID ="51435";
  private static final String CONCLAVE_ORG_ID = "GB-COH-05684804"; // Internal PPG ID

  private static final SalesforceRfx SALESFORCE_RFX = new SalesforceRfx();
  private static final SalesforceProjectTender SALESFORCE_PROJECT_TENDER = new SalesforceProjectTender();

  private static final  BuyerCompany BUYER_COMPANY = BuyerCompany.builder().id(JAGGAER_BUYER_COMPANY_ID).build();
  private static final  OwnerUser OWNER_USER = OwnerUser.builder().login(OWNER_USER_LOGIN).build();

  private static final OrganisationMapping ORG_MAPPING = OrganisationMapping.builder()
		  .id(1)
	      .externalOrganisationId(Integer.valueOf(JAGGAER_BUYER_COMPANY_ID))
	      .organisationId(CONCLAVE_ORG_ID).build();

  private static final RfxTemplateMapping RFX_TEMP_MAPPING = RfxTemplateMapping.builder()
	      .rfxReferenceCode(RFX_REFERENCE_CODE).build();
  
  private static final  AdditionalInfo additionalInfoProcurementRoute = AdditionalInfo.builder()
    		.name(ADDITIONAL_INFO_PROCUREMENT_ROUTE)
    		.type(ADDITIONAL_INFO_PROCUREMENT_ROUTE_TYPE)
    		.visibleToSupplier(0)
            .label(ADDITIONAL_INFO_PROCUREMENT_ROUTE)
            .labelLocale(ADDITIONAL_INFO_LOCALE)
            .values(
                    new AdditionalInfoValues(Arrays.asList(new AdditionalInfoValue(PROCUREMENT_ROUTE))))
            .build();
  
  private static final AdditionalInfo additionalInfoFramework = AdditionalInfo.builder().name(ADDITIONAL_INFO_FRAMEWORK_NAME)
          .label(ADDITIONAL_INFO_FRAMEWORK_NAME).labelLocale(ADDITIONAL_INFO_LOCALE)
          .values(
                  new AdditionalInfoValues(Arrays.asList(new AdditionalInfoValue(CA_NUMBER))))
          .build();

  private static final AdditionalInfo additionalInfoLot =
          AdditionalInfo.builder().name(ADDITIONAL_INFO_LOT_NUMBER).label(ADDITIONAL_INFO_LOT_NUMBER)
                  .labelLocale(ADDITIONAL_INFO_LOCALE).values(new AdditionalInfoValues(
                          Arrays.asList(new AdditionalInfoValue(LOT_NUMBER))))
                  .build();

  private static final  RfxAdditionalInfoList rfxAdditionalInfoList = new RfxAdditionalInfoList(Arrays.asList(additionalInfoProcurementRoute, additionalInfoFramework, additionalInfoLot));
  
  private static final RfxSetting rfxSetting =
          RfxSetting.builder()
					.shortDescription(SHORT_DESCRIPTION)
					.longDescription(SHORT_DESCRIPTION)
          		.rfiFlag(RFI_FLAG)
          		.value(Integer.valueOf(VALUE))
  				.templateReferenceCode(TEMPLATE_REFERENCE_CODE)
  				.tenderReferenceCode(TENDER_REF_CODE)	// was project.getExternalReferenceId()
                .buyerCompany(BUYER_COMPANY)
                .ownerUser(OWNER_USER)
                .rfxType(RFX_TYPE)
          		.qualEnvStatus(Integer.valueOf(QUAL_ENV_STATUS))                    
          		.techEnvStatus(Integer.valueOf(TECH_ENV_STATUS))                    
          		.commEnvStatus(Integer.valueOf(COMM_ENV_STATUS))
          		.visibilityEGComments(Integer.valueOf(VIS_EG_COMMENTS))
          		.rankingStrategy(RANKING_STRATEGY)
          		.publishDate(OffsetDateTime.parse(PUBLISH_DATE))
          		.closeDate(OffsetDateTime.parse(CLOSE_DATE))
          		.build();  
  
  private static final  Rfx rfx = Rfx.builder()
    		.rfxSetting(rfxSetting)
            .rfxAdditionalInfoList(rfxAdditionalInfoList)
            .build();
  
  
  private static final Optional<SubUser> JAGGAER_USER = Optional
	      .of(SubUser.builder().userId(JAGGAER_USER_ID).email(PRINCIPAL).name(BUYER_USER_NAME).build());

 
  @MockBean(answer = Answers.RETURNS_DEEP_STUBS)
  private WebClient jaggaerWebClient;

  @MockBean
  private UserProfileService userProfileService;

  @MockBean
  private ConclaveService conclaveService;

  @MockBean
  private RetryableTendersDBDelegate retryableTendersDBDelegate;

  @MockBean
  private ProcurementEventService procurementEventService;

  @MockBean
  private AgreementsServiceAPIConfig agreementsServiceAPIConfig;

  @MockBean
  private WebclientWrapper webclientWrapper;

  @MockBean
  private AgreementsService agreementsService;

  @MockBean
  private SalesforceProjectTender200Response salesforceProjectTender200Response;

  @MockBean
  private EventTransitionService eventTransitionService;
  
  @MockBean
  private RfxTemplateMappingRepo rfxTemplateMappingRepo;

  @MockBean
  private ApiKeyDetailsProvider apiKeyDetailsProvider;
  
  @Autowired
  private JaggaerAPIConfig jaggaerAPIConfig;

  @Autowired
  private ProcurementProjectService procurementProjectService;

  @Autowired
  private TendersAPIModelUtils tendersAPIModelUtils;

  @MockBean
  private JaggaerService jaggaerService;
  
  @MockBean
  private  OcdsConfig ocdsConfig;


  @BeforeAll
  static void beforeAll() {
	  
	SALESFORCE_PROJECT_TENDER.setTenderReferenceCode(TENDER_REF_CODE);
	SALESFORCE_PROJECT_TENDER.setSubject(SHORT_DESCRIPTION);
	SALESFORCE_PROJECT_TENDER.setProcurementReference("324233324");
	

	SALESFORCE_RFX.setOwnerUserLogin(PRINCIPAL);
	SALESFORCE_RFX.setShortDescription(SHORT_DESCRIPTION);
	SALESFORCE_RFX.setRfiFlag("0");
	SALESFORCE_RFX.setValue("7777");
	SALESFORCE_RFX.setQualEnvStatus("0");
	SALESFORCE_RFX.setTechEnvStatus("1");	
	SALESFORCE_RFX.setCommEnvStatus("1");
	SALESFORCE_RFX.setPublishDate("2021-01-28T12:00:00.000+00:00");
	SALESFORCE_RFX.setCloseDate("2021-02-27T12:00:00.000+00:00");
	SALESFORCE_RFX.setVisibilityEGComments("1");
	SALESFORCE_RFX.setRankingStrategy("BEST_TECHNICAL_SCORE");
	SALESFORCE_RFX.setProcurementRoute("Open Market");
	SALESFORCE_RFX.setFrameworkName("TT3209");
	SALESFORCE_RFX.setFrameworkLotNumber(LOT_NUMBER);
	SALESFORCE_RFX.setFrameworkRMNumber(CA_NUMBER);
	
	SALESFORCE_PROJECT_TENDER.setRfx(SALESFORCE_RFX);
	
	
  }


  
  @Test
  void testCreateFromSalesForceDetails() throws Exception {
		
	String key = "jgkepi7df-890g7s-8g7usidfgpoid7yf";
    when(apiKeyDetailsProvider.findDetailsByKey(key)).thenReturn(Optional
            .of(ApiKeyDetails.builder().key(key)
                .authorities(AuthorityUtils.commaSeparatedStringToAuthorityList(Authorities.ESOURCING_ROLE))
            .build()));
		
	  // Stub some objects
    var createUpdateProjectResponse = new CreateUpdateProjectResponse();
    createUpdateProjectResponse.setReturnCode(0);
    createUpdateProjectResponse.setReturnMessage("OK");
    createUpdateProjectResponse.setTenderCode(TENDER_CODE);
    createUpdateProjectResponse.setTenderReferenceCode(TENDER_REF_CODE);
    
    var createUpdateRfxResponse = new CreateUpdateRfxResponse();
    createUpdateRfxResponse.setReturnCode(0);
    createUpdateRfxResponse.setReturnMessage("OK");
    createUpdateRfxResponse.setRfxId(TENDER_REF_CODE);
    createUpdateRfxResponse.setRfxReferenceCode(RFX_REFERENCE_CODE);

    //CreateUpdateRfx
    var createUpdateRfx = new CreateUpdateRfx(OperationCode.CREATE, rfx);
 
    var procurementProject = ProcurementProject.builder()
        .id(PROC_PROJECT_ID).caNumber(CA_NUMBER).lotNumber(LOT_NUMBER)
        .externalProjectId(TENDER_CODE).externalReferenceId(TENDER_REF_CODE).projectName(PROJ_NAME)
        .createdBy(key).createdAt(Instant.now()).updatedBy(key).updatedAt(Instant.now())
        .procurementEvents(Set.of(ProcurementEvent.builder().eventType("FC").id(1).build()))
        .build();
    
    var procurementEvent = ProcurementEvent.builder()
    		.id(PROC_PROJECT_ID)
    		.ocdsAuthorityName("ocds")
    		.ocidPrefix("abc123")
    		.project(procurementProject)
    		.build();

    // Mock behaviours
    when(retryableTendersDBDelegate.findRfxTemplateMappingRfxShortDescription(CA_NUMBER + "/" + LOT_NUMBER))
    	.thenReturn(Optional.of(RFX_TEMP_MAPPING));
    when(retryableTendersDBDelegate.findOrganisationMappingByOrganisationId(any(String.class)))
    	.thenReturn(Optional.of(ORG_MAPPING));
    when(jaggaerWebClient.post().uri(jaggaerAPIConfig.getCreateProject().get("endpoint"))
            .bodyValue(any(CreateUpdateProject.class)).retrieve()
            .bodyToMono(eq(CreateUpdateProjectResponse.class))
            .block(Duration.ofSeconds(jaggaerAPIConfig.getTimeoutDuration())))
                .thenReturn(createUpdateProjectResponse);
    when(jaggaerWebClient.post().uri(jaggaerAPIConfig.getCreateRfx().get("endpoint"))
        .bodyValue(any(CreateUpdateRfx.class)).retrieve()
        .bodyToMono(eq(CreateUpdateRfxResponse.class))
        .block(Duration.ofSeconds(jaggaerAPIConfig.getTimeoutDuration())))
            .thenReturn(createUpdateRfxResponse);
    when(retryableTendersDBDelegate.save(any(ProcurementProject.class)))
        .thenReturn(procurementProject);
    when(retryableTendersDBDelegate.save(any(ProcurementEvent.class)))
    	.thenReturn(procurementEvent);
    when(retryableTendersDBDelegate.findProcurementProjectById(any(Integer.class)))
    	.thenReturn(Optional.of(procurementProject));
    when(procurementEventService.createSalesforceRfxRequest(procurementProject, SALESFORCE_PROJECT_TENDER, PRINCIPAL))
        .thenReturn(createUpdateRfx);
    when(userProfileService.resolveBuyerUserProfile(any(String.class)))
    	.thenReturn(JAGGAER_USER);

    
    var salesforceProjectTender200Response = procurementProjectService.createFromSalesforceDetails(SALESFORCE_PROJECT_TENDER);

    // Assert
    assertEquals(RFX_REFERENCE_CODE, salesforceProjectTender200Response.getRfxReferenceCode());
    assertEquals(TENDER_REF_CODE, salesforceProjectTender200Response.getTenderReferenceCode());

  }
}
