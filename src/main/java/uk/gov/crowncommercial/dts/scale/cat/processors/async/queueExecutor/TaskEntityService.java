package uk.gov.crowncommercial.dts.scale.cat.processors.async.queueExecutor;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.Timestamps;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.TaskEntity;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.TaskHistoryEntity;
import uk.gov.crowncommercial.dts.scale.cat.repo.TaskRepo;

import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
public class TaskEntityService {
    private final TaskRepo taskRepo;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persist(String principal, Task task, String recordType, String recordId, String data) {
        TaskEntity entity = new TaskEntity();
        entity.setName(recordType + recordId + task.getRunner());
        entity.setPrincipal(principal);
        entity.setData(data);
        entity.setDataClass(task.getClassName());
        entity.setTaskExecutor(task.getRunner());
        Instant instant = Instant.now();
        entity.setTobeExecutedAt(instant);
        entity.setScheduledOn(instant);
        entity.setRecordType(recordType);
        entity.setRecordId(recordId);
        entity.setStatus(Task.SCHEDULED);
        entity.setNode("self");
        entity.setTimestamps(Timestamps.createTimestamps(principal));
        taskRepo.save(entity);
        task.setId(entity.getId());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TaskEntity markInProgress(Task task) {
        TaskEntity entity = getEntity(task);
        checkProceed(entity);
        markHistoryAborted(entity);
        addHistory(entity);
        entity.setStatus(Task.INFLIGHT);
        entity.setLastExecutedOn(Instant.now());
        taskRepo.save(entity);
        return entity;
    }

    private void checkProceed(TaskEntity entity) {
        switch(entity.getStatus()){
            case Task.COMPLETED :
            case Task.FAILED:
            case Task.ABORTED:
                throw new IllegalArgumentException("This task cannot be re-processed");
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markComplete(Task task, String response) {
        TaskEntity entity = getEntity(task);
        entity.setResponse(response);
        entity.setStatus(Task.COMPLETED);
        update(entity);
        updateHistory(entity, entity.getStatus(), response);
        taskRepo.save(entity);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailure(Task task, String response) {
        TaskEntity entity = getEntity(task);
        entity.setResponse(response);
        entity.setStatus(Task.FAILED);
        update(entity);
        updateHistory(entity, entity.getStatus(), response);
        taskRepo.save(entity);
    }


    private void update(TaskEntity entity) {
        Timestamps timestamps = entity.getTimestamps();
        timestamps.setUpdatedAt(Instant.now());
        timestamps.setUpdatedBy(entity.getPrincipal());
    }

    private void markHistoryAborted(TaskEntity entity) {
        for (TaskHistoryEntity history : entity.getHistory()) {
            switch (history.getStatus()) {
                case Task.INFLIGHT:
                case Task.SCHEDULED:
                    history.setStatus(Task.ABORTED);
                    Timestamps.updateTimestamps(history.getTimestamps(), entity.getPrincipal());
            }
        }
    }

    private void updateHistory(TaskEntity entity, char completed, String response) {
        List<TaskHistoryEntity> historyList = entity.getHistory();
        if (historyList.size() > 0) {
            TaskHistoryEntity history = historyList.get(0);
            history.setStatus(completed);
            history.setResponse(response);
            Timestamps.updateTimestamps(history.getTimestamps(), entity.getPrincipal());
        } else {
            throw new IllegalStateException("Task History cannot be Empty");
        }
    }


    private void addHistory(TaskEntity entity) {
        List<TaskHistoryEntity> history = entity.getHistory();
        Instant instant = Instant.now();
        TaskHistoryEntity historyEntity;

        if (0 > history.size()) {
            TaskHistoryEntity recentEntity = history.get(0);
            if (recentEntity.getStatus() == Task.COMPLETED
                    || recentEntity.getStatus() == Task.FAILED) {
                historyEntity = createTaskHistory(entity, instant);
                history.add(historyEntity);
            } else if (recentEntity.getStatus() == Task.INFLIGHT) {
                recentEntity.setStatus(Task.ABORTED);
                Timestamps.updateTimestamps(recentEntity.getTimestamps(), entity.getPrincipal());
                historyEntity = createTaskHistory(entity, instant);
                history.add(historyEntity);
            } else {
                historyEntity = recentEntity;
            }
        } else {
            historyEntity = createTaskHistory(entity, instant);
            history.add(historyEntity);
        }

        historyEntity.setExecutedOn(Instant.now());
    }

    private static TaskHistoryEntity createTaskHistory(TaskEntity entity, Instant instant) {
        TaskHistoryEntity historyEntity;
        historyEntity = new TaskHistoryEntity();
        historyEntity.setTaskEntity(entity);
        historyEntity.setStatus(Task.INFLIGHT);
        historyEntity.setNode(entity.getNode());
        historyEntity.setScheduledOn(instant);
        historyEntity.setTimestamps(Timestamps.createTimestamps(entity.getPrincipal()));
        return historyEntity;
    }


    private TaskEntity getEntity(Task task) {
        if (null == task.getId())
            return null;

        TaskEntity entity = taskRepo.findById(task.getId()).orElse(null);

        if (null != entity)
            return entity;

        int i = 0;
        while (null == entity && i < 6) {
            sleep(1000);
            i++;
            entity = taskRepo.findById(task.getId()).orElse(null);
        }
        return entity;
    }


    private void sleep(int i) {
        try {
            Thread.sleep(i);
        } catch (InterruptedException e) {
        }
    }
}
