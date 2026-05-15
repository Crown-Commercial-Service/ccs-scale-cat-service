package uk.gov.crowncommercial.dts.scale.cat.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.crowncommercial.dts.scale.cat.clients.DMPClient;
import uk.gov.crowncommercial.dts.scale.cat.model.dmp.Supplier;
import uk.gov.crowncommercial.dts.scale.cat.model.dmp.SupplierDetail;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DMPServiceTest {

  @InjectMocks private DMPService underTest;

  @Mock private DMPClient dmpClient;

  @Test
  void testValidGetSupplierDetails() {
    // Given
    final SupplierDetail supplierDetail = new SupplierDetail();
    supplierDetail.setSuppliers(new Supplier());
    supplierDetail.getSuppliers().setId(1L);
    supplierDetail.getSuppliers().setName("Test Name");
    supplierDetail.getSuppliers().setDescription("Test Description");
    supplierDetail.getSuppliers().setDunsNumber("Test DUNS");
    supplierDetail.getSuppliers().setRegisteredName("Test Registered Name");
    when(dmpClient.getSupplierDetails(anyString(), anyString())).thenReturn(supplierDetail);

    // When
    final SupplierDetail result = underTest.getSupplierDetails("1");

    // Then
    assertNotNull(result);
    assertNotNull(result.getSuppliers());
    assertEquals(supplierDetail, result);
  }

  @Test
  void testValidNullGetSupplierDetails() {
    // Given
    when(dmpClient.getSupplierDetails(anyString(), anyString())).thenReturn(null);

    // When
    final SupplierDetail result = underTest.getSupplierDetails("1");

    // Then
    assertNull(result);
  }
}
