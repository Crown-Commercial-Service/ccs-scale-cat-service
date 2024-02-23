package uk.gov.crowncommercial.dts.scale.cat.service;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.web.reactive.function.client.WebClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.LotSupplier;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.Organization;
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
import uk.gov.crowncommercial.dts.scale.cat.repo.BuyerUserDetailsRepo;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;
import uk.gov.crowncommercial.dts.scale.cat.utils.SanitisationUtils;

/**
 *
 */
@SpringBootTest(classes = {AwardService.class, SupplierService.class},
    webEnvironment = WebEnvironment.NONE)
@Slf4j
@ContextConfiguration(classes = {ObjectMapper.class})
class AwardServiceTest {

  static final String AGREEMENT_NUMBER = "RM1234";
  static final String LOT_NUMBER = "Lot 2";

  static final String SUPPLIER_ORG_ID_1 = "GB-COH-1234567";
  static final LotSupplier LOT_SUPPLIER_1 = LotSupplier.builder()
      .organization(Organization.builder().id(SUPPLIER_ORG_ID_1).build()).build();
  static final String SUPPLIER_ORG_ID_2 = "GB-COH-7654321";
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

  @MockBean
  private JaggaerService jaggaerService;

  @MockBean
  private WebclientWrapper webclientWrapper;

  @MockBean(answer = Answers.RETURNS_DEEP_STUBS)
  private WebClient rpaServiceWebClient;

  @MockBean
  private BuyerUserDetailsRepo buyerDetailsRepo;

  @MockBean
  private DocumentTemplateResourceService documentTemplateResourceService;

  @MockBean
  private SanitisationUtils sanitisationUtils;


  @BeforeAll
  static void beforeClass() {
    ORG_MAPPING_1.setOrganisationId(SUPPLIER_ORG_ID_1);
    ORG_MAPPING_1.setExternalOrganisationId(EXT_ORG_ID_1);
    ORG_MAPPING_2.setOrganisationId(SUPPLIER_ORG_ID_2);
    ORG_MAPPING_2.setExternalOrganisationId(EXT_ORG_ID_2);
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
  
  @Test
  void testCreateAwardRfx() {
    List<OrganizationReference1> suppliersList = new ArrayList<>();
    suppliersList.add(new OrganizationReference1().id("GB-COH-1234567"));
    var rfxResponse = prepareSupplierDetails();
    var award = new Award2AllOf().suppliers(suppliersList);
    var procurementEvent = ProcurementEvent.builder().externalReferenceId(EXTERNAL_EVENT_ID)
    .externalEventId(RFX_ID).build();
    // Mock behaviours
    when(jaggaerService.getRfxWithSuppliers(RFX_ID)).thenReturn(rfxResponse);
    when(retryableTendersDBDelegate
        .findOrganisationMappingByCasOrganisationIdIn(Set.of(SUPPLIER_ORG_ID_1)))
            .thenReturn(Set.of(ORG_MAPPING_1));
    when(userProfileService.resolveBuyerUserProfile(PRINCIPAL)).thenReturn(JAGGAER_USER);
    when(validationService.validateProjectAndEventIds(PROC_PROJECT_ID, EVENT_OCID))
        .thenReturn(ProcurementEvent.builder().externalReferenceId(EXTERNAL_EVENT_ID)
            .externalEventId(RFX_ID).build());
    when(jaggaerService.awardOrPreAwardRfx(procurementEvent, JAGGAER_USER_ID, EXT_ORG_ID_1+"", AwardState.AWARD)).thenReturn("Awarded");
    // Invoke
    var awardResponse = awardService.createOrUpdateAwardRfx(PRINCIPAL, PROC_PROJECT_ID, EVENT_OCID,
        AwardState.AWARD, award, null);
    assertNotNull(awardResponse);
    verify(retryableTendersDBDelegate).updateEventDate(any(), any());
  }

}
