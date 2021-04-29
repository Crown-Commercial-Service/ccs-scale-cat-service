package uk.gov.crowncommercial.dts.scale.cat.repo;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementProject;

/**
 *
 */
@Repository
public interface ProcurementProjectRepo extends JpaRepository<ProcurementProject, UUID> {

  /* No additional methods */

}
