package uk.gov.crowncommercial.dts.scale.cat.processors.async.queueExecutor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.Timestamps;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.TaskEntity;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.TaskGroupEntity;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.TaskHistoryEntity;
import uk.gov.crowncommercial.dts.scale.cat.repo.TaskGroupRepo;
import uk.gov.crowncommercial.dts.scale.cat.repo.TaskRepo;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class TaskEntityService {
    private final TaskRepo taskRepo;
    private final TaskGroupRepo taskGroupRepo;
    private final TaskUtils taskUtils;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TaskEntity persist(String principal, Task task, String recordType, String recordId, String data) {
        TaskEntity entity = new TaskEntity();
        TaskGroupEntity groupEntity = saveTaskGroup(principal, recordType, recordId);
        entity.setName(recordType + recordId + task.getRunner());
        entity.setPrincipal(principal);
        entity.setData(data);
        entity.setDataClass(task.getClassName());
        entity.setTaskExecutor(task.getRunner());
        Instant instant = Instant.now();
        entity.setTobeExecutedAt(instant);
        entity.setScheduledOn(instant);
        entity.setGroup(groupEntity);
        entity.setRecordType(recordType);
        entity.setRecordId(recordId);
        entity.setStatus(Task.SCHEDULED);

        if(null != groupEntity.getNode())
            entity.setNode(groupEntity.getNode());
        else
            entity.setNode(taskUtils.getNodeName());

        entity.setTimestamps(Timestamps.createTimestamps(principal));
        Timestamps.updateTimestamps(entity.getTimestamps(), principal);
        taskRepo.saveAndFlush(entity);
        task.setId(entity.getId());
        task.setGroupId(entity.getGroup().getId());
        return entity;
    }

    private TaskGroupEntity saveTaskGroup(String principal, String recordType, String recordId) {
        TaskGroupEntity entity = taskGroupRepo.findActiveGroup(recordType, recordId);
        if(null != entity)
            return entity;
        entity = new TaskGroupEntity();
        entity.setName(recordType);
        entity.setReference(recordId);
        entity.setStatus('A');
        entity.setNode(taskUtils.getNodeName());
        entity.setTimestamps(Timestamps.createTimestamps(principal));
        taskGroupRepo.saveAndFlush(entity);
        return entity;
    }

    @Transactional
    public TaskHistoryEntity getLatestHistory(Task task, String stage){
        if(null == stage)
            return null;
        TaskEntity entity = getEntity(task);
        List<TaskHistoryEntity> history = entity.getHistory();
        for(TaskHistoryEntity e : history){
            if(null != e.getStage() && stage.equalsIgnoreCase(e.getStage()))
                return e;
        }
        return null;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TaskEntity markInProgress(Task task, boolean abortHistory) {
        TaskEntity entity = getEntity(task);
        TaskUtils.checkProceed(entity);
        if(abortHistory)
            markHistoryAborted(entity);
        entity.setStage(task.getTaskStage());
        addHistory(entity, abortHistory);
        entity.setStatus(Task.INFLIGHT);
        entity.setLastExecutedOn(Instant.now());
        taskRepo.save(entity);
        return entity;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markComplete(Task task, String response) {
        TaskEntity entity = markTaskStatus(task, response, Task.COMPLETED, 'I');
        if(null != entity.getGroup() && taskRepo.findPendingJobsByGroup(entity.getGroup()) == 0){
            TaskGroupEntity groupEntity = entity.getGroup();
            groupEntity.setStatus('C');
            taskGroupRepo.saveAndFlush(groupEntity);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailure(Task task, String response) {
        markTaskStatus(task, response, Task.FAILED, 'I');
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markRetry(Task task, String response, int seconds) {
        TaskEntity entity = getEntity(task);
        entity.setResponse(response);
        updateHistory(entity, Task.FAILED, response, 'I', 'S');
        entity.setTobeExecutedAt(Instant.now().plus(seconds, ChronoUnit.SECONDS));
        task.setTobeExecutedAt(entity.getTobeExecutedAt());
        entity.setStatus(Task.SCHEDULED);
        update(entity);
        taskRepo.save(entity);
    }

    private void update(TaskEntity entity) {
        Timestamps timestamps = entity.getTimestamps();
        timestamps.setUpdatedAt(Instant.now());
        timestamps.setUpdatedBy(entity.getPrincipal());
    }

    public void markHistoryAborted(TaskEntity entity) {
        for (TaskHistoryEntity history : entity.getHistory()) {
            switch (history.getStatus()) {
                case Task.INFLIGHT:
                case Task.SCHEDULED:
                    history.setStatus(Task.ABORTED);
                    Timestamps.updateTimestamps(history.getTimestamps(), entity.getPrincipal());
            }
        }
    }

    private void updateHistory(TaskEntity entity, char taskExecutionStatus, String response, char... prevStatus) {
        if(entity.getHistory().size() > 0) {
            TaskHistoryEntity history = getLatestHistory(entity);
            if (taskUtils.isStatusIn(history, prevStatus)) {
                log.debug("expected previous status is not in place, current status {}, status list {}",
                        taskExecutionStatus, prevStatus);
            }
            history.setStatus(taskExecutionStatus);
            history.setResponse(response);
            Timestamps.updateTimestamps(history.getTimestamps(), entity.getPrincipal());
        }
    }

    private TaskHistoryEntity getLatestHistory(TaskEntity entity) {
        List<TaskHistoryEntity> historyList = entity.getHistory();
        if (historyList.size() > 0) {
            return historyList.get(0);
        } else {
            throw new IllegalStateException("Task History cannot be Empty");
        }
    }




    private void addHistory(TaskEntity entity, boolean abortHistory) {
        List<TaskHistoryEntity> history = entity.getHistory();
        TaskHistoryEntity historyEntity = null;

        if (history.size() > 0) {
            TaskHistoryEntity recentEntity = history.get(0);
            if (recentEntity.getStatus() == Task.SCHEDULED) {
                recentEntity.setStatus(Task.INFLIGHT);
                recentEntity.setExecutedOn(Instant.now());
                return;
            }
            if (recentEntity.getStatus() == Task.INFLIGHT) {
                if(abortHistory) {
                    recentEntity.setStatus(Task.ABORTED);
                    Timestamps.updateTimestamps(recentEntity.getTimestamps(), entity.getPrincipal());
                }else {
                    return;
                }
            }
        }

        historyEntity = createTaskHistory(entity);
        history.add(historyEntity);
        historyEntity.setExecutedOn(Instant.now());
    }

    private static TaskHistoryEntity createTaskHistory(TaskEntity entity) {
        TaskHistoryEntity historyEntity;
        historyEntity = new TaskHistoryEntity();
        historyEntity.setTaskEntity(entity);
        historyEntity.setStatus(Task.INFLIGHT);
        historyEntity.setStage(entity.getStage());
        historyEntity.setNode(entity.getNode());
        historyEntity.setScheduledOn(null != entity.getTobeExecutedAt() ? entity.getTobeExecutedAt() : Instant.now());
        historyEntity.setTimestamps(Timestamps.createTimestamps(entity.getPrincipal()));
        return historyEntity;
    }


    private TaskEntity getEntity(Task task) {
        if (null == task.getId())
            return null;

        return taskUtils.get(()-> taskRepo.findById(task.getId()).orElse(null), 6);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markStageComplete(Task task, String response) {
        TaskEntity entity = getEntity(task);
        entity.setResponse(response);
        entity.setStatus(Task.INFLIGHT);
        update(entity);
        updateHistory(entity, Task.COMPLETED, response, 'I');
        taskRepo.saveAndFlush(entity);
    }

    public boolean isSchedulable(Task task){
        TaskEntity entity = getEntity(task);
        return taskUtils.isSchedulable(entity);
    }

    private TaskEntity markTaskStatus(Task task, String response, char status, char... prevStatus) {
        TaskEntity entity = getEntity(task);
        entity.setResponse(response);
        entity.setStatus(status);
        update(entity);
        updateHistory(entity, entity.getStatus(), response, prevStatus);
        taskRepo.save(entity);
        return entity;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TaskEntity assignToSelf(TaskEntity taskEntity) {
        TaskEntity entity = taskRepo.findById(taskEntity.getId()).orElseThrow();
        entity.setNode(taskUtils.getNodeName());
        entity.getTimestamps().setUpdatedAt(Instant.now());
        TaskGroupEntity groupEntity =  entity.getGroup();
        if(null != groupEntity){
            groupEntity.setNode(taskUtils.getNodeName());
        }
        taskRepo.saveAndFlush(entity);
        return entity;
    }
}
