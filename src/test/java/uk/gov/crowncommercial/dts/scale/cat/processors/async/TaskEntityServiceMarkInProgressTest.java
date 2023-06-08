package uk.gov.crowncommercial.dts.scale.cat.processors.async;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import uk.gov.crowncommercial.dts.scale.cat.config.EnvironmentConfig;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.Timestamps;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.TaskEntity;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.TaskHistoryEntity;
import uk.gov.crowncommercial.dts.scale.cat.processors.async.queueExecutor.Task;
import uk.gov.crowncommercial.dts.scale.cat.processors.async.queueExecutor.TaskEntityService;
import uk.gov.crowncommercial.dts.scale.cat.processors.async.queueExecutor.TaskUtils;
import uk.gov.crowncommercial.dts.scale.cat.repo.TaskGroupRepo;
import uk.gov.crowncommercial.dts.scale.cat.repo.TaskRepo;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = {TaskEntityService.class, EnvironmentConfig.class
, TaskUtils.class},
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class TaskEntityServiceTest {

    @Autowired
    private TaskEntityService taskEntityService;

    @MockBean
    private TaskRepo taskRepo;
    @MockBean
    private TaskGroupRepo taskGroupRepo;

    @Test
    public void testMarkInProgressScheduledTask(){
        Task task = getTask();
        TaskEntity entity = getTaskEntity(task, Task.SCHEDULED);
        when(taskRepo.findById(task.getId())).thenReturn(Optional.of(entity));

        when(taskRepo.save(entity)).then(invocation -> {
            TaskEntity te = invocation.getArgument(0, TaskEntity.class);
            assertEquals(Task.INFLIGHT, te.getStatus(), "Task status not properly updated");
            return te;
        });

        taskEntityService.markInProgress(task, true);

        List<TaskHistoryEntity> historyList = entity.getHistory();
        TaskHistoryEntity latestHistory = historyList.get(0);
        assertEquals(Task.INFLIGHT, latestHistory.getStatus());
    }

    @Test
    public void testMarkInProgressWIPTask(){
        testInProgressWithHistory(Task.INFLIGHT, Task.ABORTED);
    }

    @Test
    public void testMarkInProgressScheduledHistory(){
        testInProgressWithHistory(Task.SCHEDULED, Task.ABORTED);
    }

    @Test
    public void testMarkInProgressFailedHistory(){
        testInProgressWithHistory(Task.FAILED, Task.FAILED);
    }

    @Test
    public void testMarkInProgressAbortedHistory(){
        testInProgressWithHistory(Task.ABORTED, Task.ABORTED);
    }

    @Test
    public void testMarkInProgressCompletedHistory(){
        testInProgressWithHistory(Task.COMPLETED, Task.COMPLETED);
    }

    @Test
    public void testMarkInProgressCompletedTask(){
        markInProgressTobeFailed(Task.COMPLETED);
    }

    @Test
    public void testMarkInProgressFailedTask(){
        markInProgressTobeFailed(Task.FAILED);
    }

    @Test
    public void testMarkInProgressAbortedTask(){
        markInProgressTobeFailed(Task.ABORTED);
    }

    private void testInProgressWithHistory(char scheduled, char aborted) {
        Task task = getTask();
        TaskEntity entity = getTaskEntity(task, Task.INFLIGHT);
        entity.getHistory().add(getHistory(entity, scheduled));
        verifyTaskwithHistory(task, entity, aborted);
    }

    private void verifyTaskwithOpenHistory(Task task, TaskEntity entity) {
        when(taskRepo.findById(task.getId())).thenReturn(Optional.of(entity));

        when(taskRepo.save(entity)).then(invocation -> {
            TaskEntity te = invocation.getArgument(0, TaskEntity.class);
            assertEquals(Task.INFLIGHT, te.getStatus(), "Task status not properly updated");
            if(2 == te.getHistory().size()){
                TaskHistoryEntity prevHistory = te.getHistory().get(0);
                assertEquals(Task.ABORTED, prevHistory.getStatus());
            }
            return te;
        });

        taskEntityService.markInProgress(task, true);

        List<TaskHistoryEntity> historyList = entity.getHistory();
        TaskHistoryEntity latestHistory = historyList.get(1);
        assertEquals(Task.INFLIGHT, latestHistory.getStatus());
    }

    private void verifyTaskwithHistory(Task task, TaskEntity entity, char expectedPrevHistoryStatus) {
        when(taskRepo.findById(task.getId())).thenReturn(Optional.of(entity));

        when(taskRepo.save(entity)).then(invocation -> {
            TaskEntity te = invocation.getArgument(0, TaskEntity.class);
            assertEquals(Task.INFLIGHT, te.getStatus(), "Task status not properly updated");
            if(2 == te.getHistory().size()){
                TaskHistoryEntity prevHistory = te.getHistory().get(0);
                assertEquals(expectedPrevHistoryStatus, prevHistory.getStatus());
            }
            return te;
        });

        taskEntityService.markInProgress(task, true);

        List<TaskHistoryEntity> historyList = entity.getHistory();
        TaskHistoryEntity latestHistory = historyList.get(1);
        assertEquals(Task.INFLIGHT, latestHistory.getStatus());
    }

    private void markInProgressTobeFailed(char C) {
        Task task = getTask();
        TaskEntity entity = getTaskEntity(task, C);
        when(taskRepo.findById(task.getId())).thenReturn(Optional.of(entity));
        assertThrows(IllegalArgumentException.class, () -> taskEntityService.markInProgress(task, true));
    }

    private TaskEntity getTaskEntity(Task task, char status) {
        TaskEntity result = new TaskEntity();
        result.setId(task.getId());
        result.setTobeExecutedAt(task.getTobeExecutedAt());
        result.setStage(task.getTaskStage());
        result.setPrincipal(task.getPrincipal());
        result.setStatus(status);
        result.setHistory(new ArrayList<>());
        return result;
    }

    private Task getTask() {
        Task task = new Task("testUser", "StringTest", "StringTest", "sample data");
        task.setGroupId(1);
        task.setId(23L);
        task.setTobeExecutedAt(Instant.now());
        return task;
    }

    private TaskHistoryEntity getHistory(TaskEntity entity, char status){
        TaskHistoryEntity result = new TaskHistoryEntity();
        result.setStatus(status);
        result.setTaskEntity(entity);
        result.setTimestamps(Timestamps.createTimestamps("test"));

        return result;
    }
}
