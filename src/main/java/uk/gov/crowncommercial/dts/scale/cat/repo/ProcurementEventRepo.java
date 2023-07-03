package uk.gov.crowncommercial.dts.scale.cat.repo;

import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;

/**
 *
 */
@Repository
public interface ProcurementEventRepo extends JpaRepository<ProcurementEvent, Integer> {

  Optional<ProcurementEvent> findProcurementEventByIdAndOcdsAuthorityNameAndOcidPrefix(
      Integer eventIdKey, String ocdsAuthorityName, String ocidPrefix);

  Set<ProcurementEvent> findByProjectId(Integer projectId);

//  @Query("select e from ProcurementEvent e where e.tenderStatus is not null and e.tenderStatus <> :status and (:agreementId IS NULL OR e.project.caNumber = :agreementId)")
//  Set<ProcurementEvent> findEventsByTenderStatusAndAgreementId(String status, String agreementId);

  @Query(value = "select e.* from procurement_events e inner join procurement_projects pp on "
      + "pp.project_id = e.project_id where e.tender_status is not null "
      + "and e.tender_status != ?1 and pp.commercial_agreement_number = ?2", nativeQuery = true)
  Set<ProcurementEvent> findEventsByTenderStatusAndAgreementId(String status, String agreementId);

}
