package uk.gov.crowncommercial.dts.scale.cat.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.LotSupplier;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.Organization;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.OrganisationMapping;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.CompanyData;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.Supplier;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;

/**
 *
 */
@SpringBootTest(classes = {SupplierService.class}, webEnvironment = WebEnvironment.NONE)
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

  @MockBean
  private AgreementsService agreementsService;

  @MockBean
  private RetryableTendersDBDelegate retryableTendersDBDelegate;

  @Autowired
  private SupplierService supplierService;

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

}
