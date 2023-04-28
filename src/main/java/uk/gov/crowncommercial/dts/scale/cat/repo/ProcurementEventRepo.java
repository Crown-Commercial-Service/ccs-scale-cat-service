package uk.gov.crowncommercial.dts.scale.cat.repo;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementProject;

/**
 *
 */
@Repository
public interface ProcurementEventRepo extends JpaRepository<ProcurementEvent, Integer> {

  Optional<ProcurementEvent> findProcurementEventByIdAndOcdsAuthorityNameAndOcidPrefix(
      Integer eventIdKey, String ocdsAuthorityName, String ocidPrefix);

  Set<ProcurementEvent> findByProjectId(Integer projectId);
  
  List<ProcurementEvent> findByExternalReferenceId(String externalReferenceId);
  
  Optional<ProcurementEvent> findByExternalEventIdAndExternalReferenceId(String externalEventId, String externalReferenceId);

}
