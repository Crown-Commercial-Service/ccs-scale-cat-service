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

  @Query("select e from ProcurementEvent e where e.publishDate is not null and (:agreementId IS NULL OR e.project.caNumber = :agreementId)")
  Set<ProcurementEvent> findPublishedEventsByAgreementId(String agreementId);

//  @Query(value = "select e.* from procurement_events e inner join procurement_projects pp on "
//      + "pp.project_id = e.project_id "
//      + "and e.publish_date is not null and pp.commercial_agreement_number = ?1 order by updated_at desc limit 1",
//      nativeQuery = true)
//  Set<ProcurementEvent> findPublishedEventsByAgreementId(String agreementId);

}
