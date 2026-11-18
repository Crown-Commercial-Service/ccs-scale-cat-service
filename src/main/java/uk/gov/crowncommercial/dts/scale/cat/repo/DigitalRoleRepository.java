package uk.gov.crowncommercial.dts.scale.cat.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.DigitalRole;

import java.util.List;

@Repository
public interface DigitalRoleRepository extends JpaRepository<DigitalRole, Long> {

  List<DigitalRole> findByIdIn(final List<Long> ids);

  List<DigitalRole> findByProjectId(final String projectId);

  List<DigitalRole> findAllByProjectIdAndEventIdOrderByJobFamilyAscRoleAscLevelAsc(
      final String projectId, final String eventId);

    @Modifying
    @Query("DELETE FROM DigitalRole d WHERE d.projectId = :projectId AND d.eventId = :eventId")
    long deleteByProjectIdAndEventId(@Param("projectId") String projectId, @Param("eventId") String eventId);
}
