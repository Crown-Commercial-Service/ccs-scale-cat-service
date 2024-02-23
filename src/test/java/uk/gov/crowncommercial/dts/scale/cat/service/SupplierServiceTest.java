package uk.gov.crowncommercial.dts.scale.cat.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.Arrays;
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
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementProject;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.CompanyData;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.ExportRfxResponse;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.Supplier;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.SuppliersList;
import uk.gov.crowncommercial.dts.scale.cat.repo.BuyerUserDetailsRepo;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;
import uk.gov.crowncommercial.dts.scale.cat.utils.SanitisationUtils;

/**
 *
 */
@SpringBootTest(classes = {SupplierService.class},
    webEnvironment = WebEnvironment.NONE)
@Slf4j
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


  private static final Integer JAGGAER_SUPPLIER_ID = 2345678;
  private static final String JAGGAER_SUPPLIER_NAME = "Bathula Consulting";

  private static final Integer JAGGAER_SUPPLIER_ID_1 = 8765432;
  private static final String JAGGAER_SUPPLIER_NAME_1 = "Doshi Industries";

  static final ProcurementProject project = ProcurementProject.builder().build();

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
  private JaggaerService jaggaerService;

  @MockBean
  private WebclientWrapper webclientWrapper;

  @MockBean(answer = Answers.RETURNS_DEEP_STUBS)
  private WebClient rpaServiceWebClient;

  @MockBean
  private BuyerUserDetailsRepo buyerDetailsRepo;

  @MockBean
  private SanitisationUtils sanitisationUtils;


  @BeforeAll
  static void beforeClass() {
    ORG_MAPPING_1.setOrganisationId(SUPPLIER_ORG_ID_1);
    ORG_MAPPING_1.setCasOrganisationId(SUPPLIER_ORG_ID_1);
    ORG_MAPPING_1.setExternalOrganisationId(EXT_ORG_ID_1);

    ORG_MAPPING_2.setOrganisationId(SUPPLIER_ORG_ID_2);
    ORG_MAPPING_2.setCasOrganisationId(SUPPLIER_ORG_ID_2);
    ORG_MAPPING_2.setExternalOrganisationId(EXT_ORG_ID_2);
  }

  @Test
  void testResolveSuppliers() throws Exception {
    when(agreementsService.getLotSuppliers(AGREEMENT_NUMBER, LOT_NUMBER))
        .thenReturn(Set.of(LOT_SUPPLIER_1, LOT_SUPPLIER_2));

    when(retryableTendersDBDelegate
        .findOrganisationMappingByCasOrganisationIdIn(Set.of(SUPPLIER_ORG_ID_1, SUPPLIER_ORG_ID_2)))
            .thenReturn(Set.of(ORG_MAPPING_1, ORG_MAPPING_2));

    var suppliers = supplierService.resolveSuppliers(AGREEMENT_NUMBER, LOT_NUMBER);

    assertEquals(Set.of(EXT_SUPPLIER_1, EXT_SUPPLIER_2), Set.copyOf(suppliers));

    verify(agreementsService).getLotSuppliers(AGREEMENT_NUMBER, LOT_NUMBER);
    verify(retryableTendersDBDelegate).findOrganisationMappingByCasOrganisationIdIn(anySet());
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
        .findOrganisationMappingByCasOrganisationIdIn(Set.of(SUPPLIER_ORG_ID_1, SUPPLIER_ORG_ID_2)))
            .thenReturn(Set.of());

    var suppliers = supplierService.resolveSuppliers(AGREEMENT_NUMBER, LOT_NUMBER);

    assertEquals(Set.of(), Set.copyOf(suppliers));

    verify(agreementsService).getLotSuppliers(AGREEMENT_NUMBER, LOT_NUMBER);
    verify(retryableTendersDBDelegate).findOrganisationMappingByCasOrganisationIdIn(anySet());
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
