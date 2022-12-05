package uk.gov.crowncommercial.dts.scale.cat.processors.async.queueExecutor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.implementation.bytecode.Throw;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import uk.gov.crowncommercial.dts.scale.cat.processors.async.AsyncConsumer;
import uk.gov.crowncommercial.dts.scale.cat.processors.async.RetryableException;

import java.util.function.BiConsumer;

@RequiredArgsConstructor
@Slf4j
public class RunnableTask implements Runnable{
    private final Task task;
    private final ApplicationContext ctx;

    @Override
    public void run() {
        execute();
    }

    public void execute(){
        AsyncConsumer consumer = ctx.getBean(task.getRunner(), AsyncConsumer.class);
        try {
            sleep(500);
            log.info("Executing the task {} for user {}", consumer.getTaskName(), task.getPrincipal());
            consumer.accept(task.getPrincipal(), task.getData());
        }catch(RetryableException re){
            if(consumer.canRetry(re.getErrorCode(), re)){
                log.info("Rescheduling the task {} for user {}", consumer.getTaskName(), task.getPrincipal());
                // TODO Schedule for next execution;
            }
        }catch(Throwable t){
            log.error("Error while processing task {} for user {}", consumer.getTaskName(), task.getPrincipal());
            log.error("Error details", t);
            consumer.onError("UNKNOWN", t);
        }
    }

    private void sleep(int i) {
        try {
            Thread.sleep(i);
        } catch (InterruptedException e) {
        }
    }
}
