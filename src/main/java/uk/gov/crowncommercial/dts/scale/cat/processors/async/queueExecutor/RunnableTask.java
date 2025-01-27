package uk.gov.crowncommercial.dts.scale.cat.processors.async.queueExecutor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;

@RequiredArgsConstructor
@Slf4j
public class RunnableTask implements Runnable {
    private final Task task;
    private final ApplicationContext ctx;

    @Override
    public void run() {
        TaskRunner runner = ctx.getBean(TaskRunner.class);
        runner.runTask(task);
    }

    public void execute() {
        TaskRunner runner = ctx.getBean(TaskRunner.class);
        runner.execute(task);
    }

    @Override
    public boolean equals(Object obj) {
        if(null != obj && obj instanceof RunnableTask){
            RunnableTask cmp = (RunnableTask) obj;
            if(null == task.getId() || null == cmp.task.getId()){
                return false;
            }else{
                return task.getId().longValue() == cmp.task.getId().longValue();
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        return task.hashCode();
    }
}
