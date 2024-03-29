package uk.gov.crowncommercial.dts.scale.cat.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.TaskEntity;

import java.time.Instant;
import java.util.List;

@Repository
public interface TaskRepo  extends JpaRepository<TaskEntity, Long> {

    String orphanJobsQuery = "select task from TaskEntity task\n" +
            " WHERE status in :statusList and node <> :node and timestamps.updatedAt < :lastAccessTime and tobeExecutedAt < :scheduleTime";


    String delayedJobsQuery = "select task from TaskEntity task\n" +
            " WHERE status in :statusList and node = :node and timestamps.updatedAt < :lastAccessTime and tobeExecutedAt < :scheduleTime";


    @Query(value = orphanJobsQuery)
    List<TaskEntity> findOrphanTasks(@Param("node") String node, @Param("statusList") char[] statusList,
                                     @Param("lastAccessTime")Instant lastAccessTime, @Param("scheduleTime") Instant scheduledAt);


    @Query(value = delayedJobsQuery)
    List<TaskEntity> findByNodeAndStatusIn(String node, char[] statusList,
                                           @Param("lastAccessTime")Instant lastAccessTime, @Param("scheduleTime") Instant scheduledAt);
}
