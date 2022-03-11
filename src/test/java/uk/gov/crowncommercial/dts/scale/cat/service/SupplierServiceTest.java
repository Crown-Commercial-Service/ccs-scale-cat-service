package uk.gov.crowncommercial.dts.scale.cat.service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.web.reactive.function.client.WebClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.config.RPAAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.LotSupplier;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.Organization;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.OrganisationMapping;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementProject;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.ScoreAndCommentNonOCDS;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.CompanyData;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.SubUsers.SubUser;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.Supplier;
import uk.gov.crowncommercial.dts.scale.cat.model.rpa.RPAAPIResponse;
import uk.gov.crowncommercial.dts.scale.cat.model.rpa.RPAGenericData;
import uk.gov.crowncommercial.dts.scale.cat.model.rpa.RPAProcessInput;
import uk.gov.crowncommercial.dts.scale.cat.model.rpa.RPAProcessInput.RPAProcessInputBuilder;
import uk.gov.crowncommercial.dts.scale.cat.model.rpa.RPAProcessNameEnum;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;

/**
 *
 */
@SpringBootTest(classes = {SupplierService.class}, webEnvironment = WebEnvironment.NONE)
@Slf4j
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
  private static final String EVENT_OCID = "ocds-abc123-1";
  private static final Integer PROC_PROJECT_ID = 1;
  private static final String JAGGAER_USER_ID = "12345";
  private static final String CREATE_MESSAGE = "Create";
  private static final String EXTERNAL_EVENT_ID = "itt_8673";

  private static final String COMMENT_1 = "comment-1";
  private static final String COMMENT_2 = "comment-2";

  private static final String RFX_ID = "rfq_0001";
  private static final Integer JAGGAER_SUPPLIER_ID = 123456;
  private static final String JAGGAER_SUPPLIER_NAME = "Bathula Consulting";

  private static final Integer JAGGAER_SUPPLIER_ID_1 = 123457;
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

  @MockBean
  private RPAGenericService rpaGenericService;

  @Autowired
  private RPAAPIConfig rpaAPIConfig;

  @MockBean
  private WebclientWrapper webclientWrapper;

  @MockBean(answer = Answers.RETURNS_DEEP_STUBS)
  private WebClient rpaServiceWebClient;

  private static RPAGenericData request = new RPAGenericData();
  private RPAProcessInputBuilder inputBuilder = RPAProcessInput.builder();

  @BeforeAll
  static void beforeClass() {
    ORG_MAPPING_1.setOrganisationId(SUPPLIER_ORG_ID_1);
    ORG_MAPPING_1.setExternalOrganisationId(EXT_ORG_ID_1);
    ORG_MAPPING_2.setOrganisationId(SUPPLIER_ORG_ID_1);
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
  @Disabled
  void testUpdateSupplierScoreAndComment() throws JsonProcessingException {
    // Stub some objects
    var scoreAndComments = List.of(new ScoreAndCommentNonOCDS().organisationId(SUPPLIER_ORG_ID_1)
        .comment(COMMENT_1).score(90.0));

    var responseString = "";

    inputBuilder.userName(PRINCIPAL).password(rpaAPIConfig.getBuyerPwd()).ittCode(EXTERNAL_EVENT_ID)
        .score("90.0").comment(COMMENT_1).supplierName(JAGGAER_SUPPLIER_NAME);

    request.setProcessName(RPAProcessNameEnum.ASSIGN_SCORE.getValue())
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
    when(userProfileService.resolveBuyerUserByEmail(PRINCIPAL)).thenReturn(JAGGAER_USER);
    when(validationService.validateProjectAndEventIds(PROC_PROJECT_ID, EVENT_OCID))
        .thenReturn(ProcurementEvent.builder().externalReferenceId(EXTERNAL_EVENT_ID).build());

    // Invoke
    var supplierResponse = supplierService.updateSupplierScoreAndComment(PRINCIPAL, PROC_PROJECT_ID,
        EVENT_OCID, scoreAndComments);

    // Assert
    assertAll(() -> assertNotNull(supplierResponse), () -> assertEquals("", supplierResponse));

  }

}
