package uk.gov.crowncommercial.dts.scale.cat.repo;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;

/**
 *
 */
@Repository
public interface ProcurementEventRepo extends JpaRepository<ProcurementEvent, Integer> {

  Optional<ProcurementEvent> findProcurementEventByIdAndOcdsAuthorityNameAndOcidPrefix(
      Integer eventIdKey, String ocdsAuthorityName, String ocidPrefix);
}
