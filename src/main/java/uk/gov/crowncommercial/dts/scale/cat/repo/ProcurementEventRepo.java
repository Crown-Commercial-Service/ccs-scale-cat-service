package uk.gov.crowncommercial.dts.scale.cat.repo;

import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;

/**
 *
 */
@Repository
public interface ProcurementEventRepo extends JpaRepository<ProcurementEvent, Integer> {

  Optional<ProcurementEvent> findProcurementEventByIdAndOcdsAuthorityNameAndOcidPrefix(
      Integer eventIdKey, String ocdsAuthorityName, String ocidPrefix);

  void deleteByIdAndOcdsAuthorityNameAndOcidPrefix(
      Integer eventIdKey, String ocdsAuthorityName, String ocidPrefix);

  @Modifying
  @Query("""
    update ProcurementEvent e
       set e.procurementTemplatePayload = :payload
     where e.id = :eventIdKey
       and e.ocdsAuthorityName = :ocdsAuthorityName
       and e.ocidPrefix = :ocidPrefix
  """)
  int updateTemplatePayload(@Param("eventIdKey") Integer eventIdKey, @Param("ocdsAuthorityName") String ocdsAuthorityName, @Param("ocidPrefix") String ocidPrefix, @Param("payload") String payload);

  Set<ProcurementEvent> findByProjectId(Integer projectId);

  @Query("select e from ProcurementEvent e where e.publishDate is not null and (:agreementId IS NULL OR e.project.caNumber = :agreementId) order by e.updatedAt desc")
  Set<ProcurementEvent> findPublishedEventsByAgreementId(String agreementId);
}
