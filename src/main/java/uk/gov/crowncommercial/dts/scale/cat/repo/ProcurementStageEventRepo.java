package uk.gov.crowncommercial.dts.scale.cat.repo;

import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementStageEvent;

/**
 *
 */
@Repository
public interface ProcurementStageEventRepo extends JpaRepository<ProcurementStageEvent, Integer> {

    Optional<ProcurementStageEvent> findByIdAndStageNumber(Integer id, Integer stageNumber);

    Optional<ProcurementStageEvent> findProcurementEventByIdAndStageNumberAndOcdsAuthorityNameAndOcidPrefix(Integer eventIdKey, Integer stageNumber, String ocdsAuthorityName, String ocidPrefix);

    void deleteByIdAndStageNumberAndOcdsAuthorityNameAndOcidPrefix(Integer eventIdKey, Integer stageNumber, String ocdsAuthorityName, String ocidPrefix);

    @Modifying
    @Query("""
      update ProcurementStageEvent e
         set e.procurementTemplatePayload = :payload
       where e.id = :eventIdKey
         and e.stageNumber = :stageNumber
         and e.ocdsAuthorityName = :ocdsAuthorityName
         and e.ocidPrefix = :ocidPrefix
    """)
    int updateTemplatePayload(@Param("eventIdKey") Integer eventIdKey, @Param("stageNumber") Integer stageNumber, @Param("ocdsAuthorityName") String ocdsAuthorityName, @Param("ocidPrefix") String ocidPrefix, @Param("payload") String payload);
}
