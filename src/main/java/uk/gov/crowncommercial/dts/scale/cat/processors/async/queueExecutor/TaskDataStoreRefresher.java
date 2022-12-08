package uk.gov.crowncommercial.dts.scale.cat.processors.async.queueExecutor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import uk.gov.crowncommercial.dts.scale.cat.config.ExperimentalFlagsConfig;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.TaskEntity;
import uk.gov.crowncommercial.dts.scale.cat.repo.TaskRepo;

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

    @Scheduled(cron = "*/5 * * * *")
    public void loadOrphanTasksFromDataStore() {
        if(!experimentalFlags.isAsyncOrphanJobsLoader())
            return;

        char[] status = {'I', 'S'};
        Instant checkTime = Instant.now().minus(15, ChronoUnit.MINUTES);
        List<TaskEntity> orphanTasks = taskRepo.findOrphanTasks("self", status, checkTime, checkTime);
        if(orphanTasks.size() > 0) {
            log.info("Retrieved {} orphan tasks from the database ", orphanTasks.size());
            asyncExecutor.loadFromDataStore(orphanTasks);
        }else{
            log.trace("No orphan tasks loaded from the database");
        }

    }

    @Scheduled(cron = "*/5 * * * *")
    public void loadTasksFromDataStore() {
        if(!experimentalFlags.isAsyncMissedJobsLoader())
            return;

        char[] status = {'I', 'S'};
        Instant checkTime = Instant.now().minus(15, ChronoUnit.MINUTES);
        List<TaskEntity> taskEntities = taskRepo.findByNodeAndStatusIn("self", status);
        if (taskEntities.size() > 0) {
            log.info("Retrieved {}  tasks from the database ", taskEntities.size());
            asyncExecutor.loadFromDataStore(taskEntities);
        }else{
            log.trace("No pending tasks loaded from the database");
        }
    }

    public void initFromDataStore() {
        if(!experimentalFlags.isAsyncResumeJobsOnStartup())
            return;

        char[] status = {'I', 'S'};
        Instant checkTime = Instant.now().minus(15, ChronoUnit.MINUTES);
        List<TaskEntity> taskEntities = taskRepo.findByNodeAndStatusIn("self", status);
        if (taskEntities.size() > 0) {
            log.info("loading {} pending tasks from the database ", taskEntities.size());
            asyncExecutor.loadFromDataStore(taskEntities);
        } else
            log.info("No pending tasks from the database");

    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        initFromDataStore();
    }

}
