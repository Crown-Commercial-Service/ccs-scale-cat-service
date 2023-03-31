package uk.gov.crowncommercial.dts.scale.cat.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.SupplierLinkEntity;

import java.util.List;

public interface SupplierLinkRepo extends JpaRepository<SupplierLinkEntity, Integer> {
    SupplierLinkEntity findByBravoId(Integer bravoId);

    SupplierLinkEntity findByCohNumber(String cohNumber);

    SupplierLinkEntity findByDunsNumber(String dunsNumber);

    List<SupplierLinkEntity> findByDunsNumberOrCohNumber(String dunsNumber, String cohNumber);

    SupplierLinkEntity findByVatNumber(String vatNumber);

    SupplierLinkEntity findByNhsNumber(String nhsNumber);
}
