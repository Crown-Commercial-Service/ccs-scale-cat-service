package uk.gov.crowncommercial.dts.scale.cat.repo;

import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementProject;

/**
 *
 */
@Repository
public interface ProcurementProjectRepo extends JpaRepository<ProcurementProject, Integer> {

    List<ProcurementProject> findByExternalProjectIdIn(Set<String> externalProjectIds);
    
    @Query(value = "select pp.* from procurement_projects pp"
        + " inner join procurement_events pe on pe.project_id = pp.project_id"
        + " where pp.commercial_agreement_number = :agreementId and pe.publish_date is not null"
        + " group by pp.project_id", nativeQuery = true)
    Set<ProcurementProject> findPublishedEventsByAgreementId(String agreementId);
}
