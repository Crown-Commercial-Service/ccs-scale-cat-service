package uk.gov.crowncommercial.dts.scale.cat.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementProject;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 *
 */
@Repository
public interface ProcurementProjectRepo extends JpaRepository<ProcurementProject, Integer> {

    List<ProcurementProject> findByExternalProjectIdIn(Set<String> externalProjectIds);

    Optional <ProcurementProject> findByExternalReferenceId(String externalProjectId);

}
