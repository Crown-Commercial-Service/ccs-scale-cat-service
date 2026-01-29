package uk.gov.crowncommercial.dts.scale.cat.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.GCloudEProcurementEntity;

import java.util.Optional;

/**
 * GCloud e-procurement repo to perform CRUD operations.
 */
public interface GCloudEProcurementRepo extends JpaRepository<GCloudEProcurementEntity, Integer> {

    Optional<GCloudEProcurementEntity> findByAssessmentId(Integer assessmentId);

    GCloudEProcurementEntity findByAssessmentIdAndCreatedBy(Integer assessmentId, String createdBy);
}