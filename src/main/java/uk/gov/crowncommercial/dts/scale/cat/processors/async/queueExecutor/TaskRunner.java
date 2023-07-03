package uk.gov.crowncommercial.dts.scale.cat.processors.async.queueExecutor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import uk.gov.crowncommercial.dts.scale.cat.processors.async.AsyncConsumer;
import uk.gov.crowncommercial.dts.scale.cat.processors.async.RetryableException;

import jakarta.transaction.Transactional;

@Component
@Scope("prototype")
@RequiredArgsConstructor
@Slf4j
public class TaskRunner{
    private final ApplicationContext ctx;
    private final TaskEntityService taskEntityService;

//    @Transactional
    public void runTask(Task task){
        if(null == task.getId()){
            throw new IllegalArgumentException("Task must be persisted in database before execution");
        }
        AsyncConsumer consumer = ctx.getBean(task.getRunner(), AsyncConsumer.class);
        taskEntityService.markInProgress(task);
        try {
            String response = execute(task, consumer);
            taskEntityService.markComplete(task, response);
        } catch (RetryableException re) {
            if (consumer.canRetry(re.getErrorCode(), re)) {
                log.info("Rescheduling the task {} for user {}", consumer.getTaskName(), task.getPrincipal());
                String response = "Rescheduled. "
                        + "::"  + consumer.onError(re.getErrorCode(), re.getCause());
                taskEntityService.markRetry(task, response);
            }else{
                markFailure(task, consumer, re.getCause());
            }
        } catch (Throwable t) {
            markFailure(task, consumer, t);
        }
    }

    private void markFailure(Task task, AsyncConsumer consumer, Throwable t) {
        log.error("Error while processing task {} for user {}", consumer.getTaskName(), task.getPrincipal());
        log.error("Error details", t);
        String response = consumer.onError("UNKNOWN", t);
        taskEntityService.markFailure(task, response);
    }


    @Transactional
    public void execute(Task task) {
        AsyncConsumer consumer = ctx.getBean(task.getRunner(), AsyncConsumer.class);
        execute(task, consumer);
    }



    private String execute(Task task,AsyncConsumer consumer) {
        log.info("Executing the task {} with data {}, for user {}", consumer.getTaskName(),consumer.getIdentifier(task.getData()),  task.getPrincipal());
        return consumer.accept(task.getPrincipal(), task.getData());
    }


}
