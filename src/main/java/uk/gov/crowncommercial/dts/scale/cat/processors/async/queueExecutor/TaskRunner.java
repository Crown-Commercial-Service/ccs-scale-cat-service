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
        System.out.println("\n=== Starting Task Execution ===");
        System.out.println("Input Task Details:");
        System.out.println("Task ID: " + task.getId());
        System.out.println("Task Principal: " + task.getPrincipal());
        System.out.println("Task Runner: " + task.getRunner());
        if(null == task.getId()){
            throw new IllegalArgumentException("Task must be persisted in database before execution");
        }
        System.out.println("\nGetting AsyncConsumer Bean from Application Context");
        AsyncConsumer consumer = ctx.getBean(task.getRunner(), AsyncConsumer.class);
        System.out.println("AsyncConsumer Bean Retrieved: " + consumer.getClass().getSimpleName());
        System.out.println("\nMarking Task as In-Progress");
        taskEntityService.markInProgress(task);
        try {
            String response = execute(task, consumer);
            System.out.println("Task Execution Successful. Response: " + response);
            taskEntityService.markComplete(task, response);
        } catch (RetryableException re) {
            System.out.println("\nCaught RetryableException:");
            System.out.println("Error Code: " + re.getErrorCode());
            System.out.println("Error Message: " + re.getMessage());
            System.out.println("Can Retry? " + consumer.canRetry(re.getErrorCode(), re));
            if (consumer.canRetry(re.getErrorCode(), re)) {
                System.out.println("\nRescheduling the task...");
                System.out.println("Task Name: " + consumer.getTaskName());
                System.out.println("Task Principal: " + task.getPrincipal());
                log.info("Rescheduling the task {} for user {}", consumer.getTaskName(), task.getPrincipal());
                String response = "Rescheduled. "
                        + "::"  + consumer.onError(re.getErrorCode(), re.getCause());
                System.out.println("Rescheduled Response: " + response);
                taskEntityService.markRetry(task, response);
            }else{
                markFailure(task, consumer, re.getCause());
            }
        } catch (Throwable t) {
            System.out.println("\nUnexpected Exception Occurred:");
            System.out.println("Exception Type: " + t.getClass().getSimpleName());
            System.out.println("Exception Message: " + t.getMessage());
            System.out.println("Marking Task as Failed...");
            markFailure(task, consumer, t);
        }
        System.out.println("\n=== Task Execution Completed ===");
    }

    private void markFailure(Task task, AsyncConsumer consumer, Throwable t) {
        log.error("Error while processing task {} for user {}", consumer.getTaskName(), task.getPrincipal());
        log.error("Error details", t);
        System.out.println("\nMarking Task as Failed:");
        System.out.println("Task Name: " + consumer.getTaskName());
        System.out.println("Task Principal: " + task.getPrincipal());
        String response = consumer.onError("UNKNOWN", t);
        System.out.println("Failure Response: " + response);
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
