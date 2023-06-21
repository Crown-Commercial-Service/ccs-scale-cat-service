package uk.gov.crowncommercial.dts.scale.cat.processors.async.queueExecutor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;

import java.util.Objects;
import java.util.function.Consumer;

@RequiredArgsConstructor
@Slf4j
public class RunnableTask implements Runnable {
    private final Task task;
    private final ApplicationContext ctx;
    private final Consumer<Task> onComplete;

    @Override
    public void run() {
        try {
            TaskRunner runner = ctx.getBean(TaskRunner.class);
            runner.runTask(task);
        }finally {
            onComplete.accept(task);
        }
    }

    public void execute() {
        TaskRunner runner = ctx.getBean(TaskRunner.class);
        runner.execute(task);
    }

    @Override
    public boolean equals(Object obj) {
        if(null != obj && obj instanceof RunnableTask){
            RunnableTask cmp = (RunnableTask) obj;
            return Objects.equals(task.getId(), cmp.task.getId());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return task.hashCode();
    }
}
