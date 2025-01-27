package uk.gov.crowncommercial.dts.scale.cat.service;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import uk.gov.crowncommercial.dts.scale.cat.model.SupplierLink;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.SupplierLinkEntity;
import uk.gov.crowncommercial.dts.scale.cat.repo.SupplierLinkRepo;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = {SupplierLinkService.class, TenderDBSupplierLinkService.class},
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Slf4j
public class SupplierLinkServiceTest {

    private static final String DUNS_NUMBER = "123456789";
    private static final String COH_NUMBER = "12345678";

    @MockBean
    private SupplierLinkRepo supplierLinkRepo;

    @Autowired
    private SupplierLinkService supplierLinkService;

    @Test
    void testGetByDunsNumber() throws Exception{
        SupplierLinkEntity entity = new SupplierLinkEntity();
        when(supplierLinkRepo.findByDunsNumber(DUNS_NUMBER)).thenReturn(entity);
        SupplierLink supplierLink = supplierLinkService.getByDuns(DUNS_NUMBER);
        assertNotNull(supplierLink, "Supplier Link not retrieved");
    }

}
