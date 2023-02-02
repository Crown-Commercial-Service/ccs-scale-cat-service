package uk.gov.crowncommercial.dts.scale.cat;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.crowncommercial.dts.scale.agreement.model.dto.SupplierDetails;
import uk.gov.crowncommercial.dts.scale.agreement.repo.LotRepo;


import java.util.List;

@Service
@RequiredArgsConstructor
public class AgreementService {
    private final LotRepo lotRepo;

    @Transactional(
            value = "agreementTransactionManager",
    propagation = Propagation.REQUIRES_NEW)
    public List<SupplierDetails> getSuppliers(){
        return lotRepo.findSuppliersByAgreement("RM1043.8");
    }

    public List<SupplierDetails> getSuppliers(String lotNumber){
        return lotRepo.findSuppliersByAgreementLot("RM1043.8", lotNumber);
    }
}
