package uk.gov.crowncommercial.dts.scale.cat.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementProject;

import java.util.Optional;

/**
 *
 */
@Repository
public interface ProcurementProjectRepo extends JpaRepository<ProcurementProject, Integer> {

    Optional<ProcurementProject> findByExternalProjectId(String externalProjectId);
}
