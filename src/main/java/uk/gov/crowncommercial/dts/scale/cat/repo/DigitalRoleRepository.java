package uk.gov.crowncommercial.dts.scale.cat.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.DigitalRole;

import java.util.List;

@Repository
public interface DigitalRoleRepository extends JpaRepository<DigitalRole, Long> {

  List<DigitalRole> findAllByProjectIdAndEventIdOrderByJobFamilyAscRoleAscLevelAsc(
      final String projectId, final String eventId);
}
