package uk.gov.crowncommercial.dts.scale.cat.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProjectUserMapping;

import java.util.Set;

/**
 *
 */
@Repository
public interface ProjectUserMappingRepo extends JpaRepository<ProjectUserMapping, Integer> {

  @Query("SELECT p " +
          "FROM ProjectUserMapping p inner join p.event e " +
          "WHERE p.event.project.id = :projectId " )
  Set<ProjectUserMapping> findByProjectId(Integer projectId);
}
