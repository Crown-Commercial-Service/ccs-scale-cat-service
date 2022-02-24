package uk.gov.crowncommercial.dts.scale.cat.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProjectUserMapping;

import java.util.Set;

/**
 *
 */
@Repository
public interface ProjectUserMappingRepo extends JpaRepository<ProjectUserMapping, Integer> {

  Set<ProjectUserMapping> findByProjectId(Integer projectId);
}
