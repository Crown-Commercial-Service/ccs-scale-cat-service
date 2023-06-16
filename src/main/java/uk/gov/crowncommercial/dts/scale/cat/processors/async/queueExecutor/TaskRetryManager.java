package uk.gov.crowncommercial.dts.scale.cat.processors.async.queueExecutor;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.TaskEntity;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.TaskHistoryEntity;
import uk.gov.crowncommercial.dts.scale.cat.repo.TaskRepo;

import jakarta.transaction.Transactional;
import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class TaskRetryManager {
    private final TaskRepo taskRepo;
    private static final int NUMBER_OF_RETRIES = 3;
    public boolean canSchedule(TaskEntity taskEntity){
        List<TaskHistoryEntity> history = taskEntity.getHistory().stream().filter(t -> (t.getStatus() == 'F')).collect(Collectors.toList());
        return history.size() < NUMBER_OF_RETRIES;
    }

    @Transactional
    public boolean canSchedule(Task task, int i) {
        TaskEntity taskEntity = taskRepo.findById(task.getId()).orElse(null);
        if(null != taskEntity){
            List<TaskHistoryEntity> history = taskEntity.getHistory().stream().filter(t -> (t.getStatus() == 'F')).collect(Collectors.toList());
            return history.size() < (NUMBER_OF_RETRIES - i);
        }
        return false;
    }

    /**
     * return interval in seconds
     * @param task
     * @return
     */
    public int getInterval(Task task) {
        return 5*60;
    }
}
