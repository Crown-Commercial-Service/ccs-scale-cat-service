package uk.gov.crowncommercial.dts.scale.cat.processors.async.queueExecutor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.TaskHistoryEntity;
import uk.gov.crowncommercial.dts.scale.cat.processors.async.*;

import javax.transaction.Transactional;
import java.util.List;

@Component
@Scope("prototype")
@RequiredArgsConstructor
@Slf4j
public class TaskRunner{
    private final ApplicationContext ctx;
    private final TaskEntityService taskEntityService;
    private final TaskRetryManager retryManager;

    public void runTask(Task task){
        if(null == task.getId()){
            throw new IllegalArgumentException("Task must be persisted in database before execution");
        }
        AsyncConsumer consumer = ctx.getBean(task.getRunner(), AsyncConsumer.class);
        if(consumer instanceof AsyncMultiConsumer<?>){
            if(null == task.getTaskStage())
                task.setTaskStage(((AsyncMultiConsumer<?>) consumer).getAllTasks().get(0));
            execMultiConsumer(task, (AsyncMultiConsumer) consumer);
        }else{
            execSingleConsumer(task, consumer);
        }
    }

    public void execSingleConsumer(Task task, AsyncConsumer consumer){
        try {
            markInProgress(task, consumer);
            String response = execute(task, consumer, consumer.getIdentifier(task.getData()));
            markComplete(task, consumer, response);
        } catch (Throwable t) {
            handleException(consumer, task, t);
        }
    }

    public void execMultiConsumer(Task task, AsyncMultiConsumer consumer){
        final String identifier = consumer.getIdentifier(task.getData());
        final int start = consumer.getTaskIndex(task.getTaskStage());
        List<String> taskNames = consumer.getAllTasks();

        try {
            markInProgress(task, consumer);
            String response = null;
            for(int i = start; i < taskNames.size(); i++){
                String currentStage = taskNames.get(i);

                TaskHistoryEntity history = taskEntityService.getLatestHistory(task, currentStage);
                if (null != history && history.getStatus() == 'C') {
                    continue;
                }
                response = executeStage(task, consumer, identifier, currentStage);
            }
            markComplete(task, consumer, response);
        } catch (Throwable t) {
            handleException(consumer, task, t);
        }
    }

    private String executeStage(Task task, AsyncMultiConsumer consumer, String identifier, String taskName) {
        String response;
        task.setTaskStage(taskName);
        taskEntityService.markInProgress(task, false);
        TaskConsumer taskConsumer = consumer.getTaskConsumer(taskName);
        response = execute(task, taskConsumer, identifier);
        taskEntityService.markStageComplete(task, response);
        return response;
    }

    private void handleException(AsyncConsumer consumer, Task task, Throwable t){
        ErrorHandler handler = getErrorHandler(consumer, t);

        if(null != handler){
            if(handler.canRetry(t) && retryManager.canSchedule(task, 1)){
                markRetry(consumer, task, t, handler);
            }else
                markFailure(task, consumer, t, handler.getMessage(t));
        }else{
            log.error("No Error handler found to handle the exception ", t.getClass().getCanonicalName()
                    + ", " + t.getMessage());
            markFailure(task, consumer, t, t.getMessage());
        }
    }

    private static ErrorHandler getErrorHandler(AsyncConsumer consumer, Throwable t) {
        List<ErrorHandler> errorHandlers= consumer.getErrorHandlers();
        for(ErrorHandler eh : errorHandlers){
            if(eh.canHandle(t)) {
                return eh;
            }
        }
        return null;
    }



    @Transactional
    public void execute(Task task) {
        AsyncConsumer consumer = ctx.getBean(task.getRunner(), AsyncConsumer.class);
        if(consumer instanceof AsyncMultiConsumer<?>){
            execute(task, (AsyncMultiConsumer) consumer);
        }else
            execute(task, consumer, consumer.getIdentifier(task.getData()));
    }

    private void execute(Task task, AsyncMultiConsumer consumer) {
        List<String> taskNames = consumer.getAllTasks();
        String identifier = consumer.getIdentifier(task.getData());
        for(String taskName : taskNames){
            execute(task, consumer.getTaskConsumer(taskName), identifier);
        }
    }


    private String execute(Task task, TaskConsumer consumer, String identifier) {
        log.info("Executing the task {} with data {}, for user {}", consumer.getTaskName(), identifier,  task.getPrincipal());
        return consumer.accept(task.getPrincipal(), task.getData());
    }

    private void markComplete(Task task, AsyncConsumer consumer, String response) {
        taskEntityService.markComplete(task, response);
        consumer.onStatusChange(task.getPrincipal(), task.getData(), AsyncTaskStatus.COMPLETED);
    }

    private void markInProgress(Task task, AsyncConsumer consumer) {
        taskEntityService.markInProgress(task, true);
        consumer.onStatusChange(task.getPrincipal(), task.getData(), AsyncTaskStatus.IN_FLIGHT);
    }

    private void markRetry(AsyncConsumer consumer, Task task, Throwable t, ErrorHandler handler) {
        log.info("Rescheduling the task {} for user {}", consumer.getTaskName(), task.getPrincipal());
        String response = "Rescheduled. "
                + "::"  + handler.getMessage(t);
        taskEntityService.markRetry(task, response, retryManager.getInterval(task));
        consumer.onStatusChange(task.getPrincipal(), task.getData(), AsyncTaskStatus.RETRY);
    }

    private void markFailure(Task task, AsyncConsumer consumer, Throwable t, String message) {
        log.error("Error while processing task {} for user {}", consumer.getTaskName(), task.getPrincipal());
        log.error("Error details", t);
        taskEntityService.markFailure(task, message);
        consumer.onStatusChange(task.getPrincipal(), task.getData(), AsyncTaskStatus.FAILED);
    }
}
