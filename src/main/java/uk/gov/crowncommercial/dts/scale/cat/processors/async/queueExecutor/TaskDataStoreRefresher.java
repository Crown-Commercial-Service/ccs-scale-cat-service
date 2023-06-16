package uk.gov.crowncommercial.dts.scale.cat.processors.async.queueExecutor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.repository.query.Param;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import uk.gov.crowncommercial.dts.scale.cat.config.EnvironmentConfig;
import uk.gov.crowncommercial.dts.scale.cat.config.ExperimentalFlagsConfig;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.TaskEntity;
import uk.gov.crowncommercial.dts.scale.cat.repo.TaskRepo;

import jakarta.transaction.Transactional;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class TaskDataStoreRefresher {
    private final TaskRepo taskRepo;
    private final QueuedAsyncExecutor asyncExecutor;
    private final ExperimentalFlagsConfig experimentalFlags;
    private final EnvironmentConfig environmentConfig;
    private final int TASK_LOAD_INTERVAL = 5;

    private static final int ORPHAN_LOAD_INTERVAL = 15;

    @Scheduled(fixedRate = ORPHAN_LOAD_INTERVAL * 60 * 1000, initialDelay = 30 * 1000)
    @Transactional
    public void loadOrphanTasksFromDataStore() {
        if(!experimentalFlags.isAsyncOrphanJobsLoader())
            return;
        String category = "orphan";
        char[] statusList = {'I', 'S'};
        Instant checkTime = Instant.now().minus(ORPHAN_LOAD_INTERVAL , ChronoUnit.MINUTES);
        List<TaskEntity> taskEntities = taskRepo.findOrphanTasks(environmentConfig.getServiceInstance(), statusList, checkTime, checkTime);
        loadTasks(category, taskEntities);
    }

    @Scheduled(fixedRate = TASK_LOAD_INTERVAL * 60 * 1000, initialDelay = 5*1000)
    @Transactional
    public void loadTasksFromDataStore() {
        if(!experimentalFlags.isAsyncMissedJobsLoader())
            return;

        char[] status = {'I', 'S'};
        Instant checkTime = Instant.now().minus(TASK_LOAD_INTERVAL, ChronoUnit.MINUTES);
        Instant tobeExecutedAt = Instant.now().plus(TASK_LOAD_INTERVAL, ChronoUnit.MINUTES);
        loadData(status, checkTime, tobeExecutedAt, "pending");
    }

    @Transactional
    public void initFromDataStore() {
        if(!experimentalFlags.isAsyncResumeJobsOnStartup())
            return;

        char[] status = {'I', 'S'};
        Instant checkTime = Instant.now().minus(1, ChronoUnit.SECONDS);
        Instant tobeExecutedAt = Instant.now().plus(TASK_LOAD_INTERVAL, ChronoUnit.MINUTES);
        loadData(status, checkTime, tobeExecutedAt, "initial");
    }


    public void loadData(char[] statusList,
                         @Param("lastAccessTime")Instant lastAccessTime, @Param("scheduleTime") Instant scheduledAt, String category) {

        List<TaskEntity> taskEntities = taskRepo.findByNodeAndStatusIn(environmentConfig.getServiceInstance(), statusList, lastAccessTime, scheduledAt);
        loadTasks(category, taskEntities);
    }

    private void loadTasks(String category, List<TaskEntity> taskEntities) {
        if (taskEntities.size() > 0) {
            log.info("Retrieved {} {}  tasks from the database ", taskEntities.size(), category);
            asyncExecutor.loadFromDataStore(taskEntities);
        }else{
            log.trace("No {} tasks loaded from the database", category);
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void onStartup() {
//        initFromDataStore();
    }

}
