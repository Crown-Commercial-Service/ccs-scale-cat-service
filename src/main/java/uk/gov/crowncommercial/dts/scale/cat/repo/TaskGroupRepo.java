package uk.gov.crowncommercial.dts.scale.cat.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.TaskEntity;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.TaskGroupEntity;

import java.time.Instant;
import java.util.List;

@Repository
public interface TaskGroupRepo extends JpaRepository<TaskGroupEntity, Integer> {
    String activeTaskGroup = "select taskGroup from TaskGroupEntity taskGroup\n" +
            " WHERE status = 'A' and name = :groupName and reference = :groupReference";

    @Query(value = activeTaskGroup)
    TaskGroupEntity findActiveGroup(@Param("groupName") String groupName, @Param("groupReference") String reference);
}
