package uk.gov.crowncommercial.dts.scale.cat.repo;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProjectUserMapping;

/**
 *
 */
@Repository
public interface ProjectUserMappingRepo extends JpaRepository<ProjectUserMapping, Integer>, JpaSpecificationExecutor<ProjectUserMapping> {

  Set<ProjectUserMapping> findByProjectId(Integer projectId);

  void deleteByProjectId(Integer projectId);

  Set<ProjectUserMapping> findByUserId(String userId);

  List<ProjectUserMapping> findByUserId(String userId, Pageable pageable);

  Optional<ProjectUserMapping> findByProjectIdAndUserId(Integer projectId, String userId);
}
