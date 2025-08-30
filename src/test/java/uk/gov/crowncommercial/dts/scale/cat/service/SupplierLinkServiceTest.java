package uk.gov.crowncommercial.dts.scale.cat.service;


import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.crowncommercial.dts.scale.cat.model.SupplierLink;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.SupplierLinkEntity;
import uk.gov.crowncommercial.dts.scale.cat.repo.SupplierLinkRepo;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SupplierLinkServiceTest {

    private static final String DUNS_NUMBER = "123456789";
    private static final String COH_NUMBER = "12345678";

    @Mock
    private SupplierLinkRepo supplierLinkRepo;

    @InjectMocks
    private TenderDBSupplierLinkService supplierLinkService;

    @Test
    void testGetByDunsNumber() throws Exception{
        SupplierLinkEntity entity = new SupplierLinkEntity();
        when(supplierLinkRepo.findByDunsNumber(DUNS_NUMBER)).thenReturn(entity);
        SupplierLink supplierLink = supplierLinkService.getByDuns(DUNS_NUMBER);
        assertNotNull(supplierLink, "Supplier Link not retrieved");
    }

}
