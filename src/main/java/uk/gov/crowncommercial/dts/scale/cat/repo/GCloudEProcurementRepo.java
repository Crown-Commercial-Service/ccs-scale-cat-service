package uk.gov.crowncommercial.dts.scale.cat.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.GCloudEProcurementEntity;

/**
 * GCloud e-procurement repo to perform CRUD operations.
 */
public interface GCloudEProcurementRepo extends JpaRepository<GCloudEProcurementEntity, Integer> {

    GCloudEProcurementEntity findByCreatedBy(String createdBy);
}