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

  Set<ProjectUserMapping> findByProjectId(Integer projectId);

  @Query(value = "select * from project_user_mapping pum " +
          "where project_id in " +
          "(select project_id  from project_user_mapping where  user_id = :userId) " ,nativeQuery = true)
  Set<ProjectUserMapping> findByUserId(String userId);
}
