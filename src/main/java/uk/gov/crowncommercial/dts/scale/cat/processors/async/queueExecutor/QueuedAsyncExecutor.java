package uk.gov.crowncommercial.dts.scale.cat.processors.async.queueExecutor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import uk.gov.crowncommercial.dts.scale.cat.config.ApplicationFlagsConfig;
import uk.gov.crowncommercial.dts.scale.cat.config.ExperimentalFlagsConfig;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.TaskEntity;
import uk.gov.crowncommercial.dts.scale.cat.processors.async.AsyncConsumer;
import uk.gov.crowncommercial.dts.scale.cat.processors.async.AsyncExecutor;
import uk.gov.crowncommercial.dts.scale.cat.processors.async.AsyncTaskStatus;
import uk.gov.crowncommercial.dts.scale.cat.processors.async.TaskScheduler;

import jakarta.transaction.Transactional;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Component
@Slf4j
@Scope("singleton")
public class QueuedAsyncExecutor implements AsyncExecutor, TaskScheduler {
    private final ThreadPoolTaskExecutor taskExecutor;
    private final ApplicationContext ctx;
    private final QueueManager queueManager;
    private final Consumer<Task> onSyncTaskComplete = (task) -> {};
    private final ApplicationFlagsConfig applicationFlags;
    private final ExperimentalFlagsConfig experimentalFlags;
    private final TaskEntityService taskEntityService;
    private final TaskRetryManager taskRetryManager;
    private final ObjectMapper mapper = new ObjectMapper();

    private final Consumer<Task> onAsyncTaskComplete;

    public QueuedAsyncExecutor(@Qualifier("comExecutor") ThreadPoolTaskExecutor executor, ApplicationContext ctx,
                               ApplicationFlagsConfig applicationFlags, ExperimentalFlagsConfig flags, TaskEntityService taskEntityService, TaskRetryManager taskRetryManager) {
        this.taskExecutor = executor;
        this.ctx = ctx;
        this.taskRetryManager = taskRetryManager;
        this.experimentalFlags = flags;
        this.applicationFlags = applicationFlags;
        this.taskEntityService = taskEntityService;
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        mapper.registerModule(new JavaTimeModule());

        queueManager = new QueueManager(executor);

        onAsyncTaskComplete = (task) -> {
            queueManager.removeInflightTask(task);
            if(taskEntityService.isSchedulable(task)){
                schedule(task);
            }
        };

    }

    public void startup() {

    }

    private String getClassName(Object data) {
        if (null != data) {
            return data.getClass().getCanonicalName();
        }
        return null;
    }

    @Override
    public <T> void submit(String principal, Class<? extends AsyncConsumer<T>> clazz, T data, String recordType, String recordId) {
        if (experimentalFlags.isAsyncExecutorEnabled()) {
            Task task = new Task(principal, getSpringName(clazz), getClassName(data), data);
            AsyncConsumer consumer = ctx.getBean(task.getRunner(), AsyncConsumer.class);
            taskEntityService.persist(principal, task, recordType, recordId, writeData(task.getData()));
            consumer.onStatusChange(principal, data, AsyncTaskStatus.SCHEDULED);
//            if(taskEntityService.isSchedulable(task))  --  this should be uncommented after group behavior is verified
                schedule(task);
        } else {
            execute(principal, clazz, data);
        }
    }

    @Scheduled(fixedRate = 15 * 1000)
    public void loadFromScheduled(){
        if(queueManager.scheduleSize() > 0) {
            List<Task> tempList = new ArrayList(queueManager.getScheduledTasks());
            for (Task task : tempList) {
                schedule(task);
            }
        }
    }

    private boolean canExecuteNow(Task task){
        return null == task.getTobeExecutedAt() || task.getTobeExecutedAt().isBefore(Instant.now());
    }

    @Transactional
    public void loadFromDataStore(List<TaskEntity> taskEntities) {
        for (TaskEntity taskEntity : taskEntities) {
            if(queueManager.queueFull())
                break;

            if(taskRetryManager.canSchedule(taskEntity)){
                TaskEntity scheduledEntity = taskEntityService.assignToSelf(taskEntity);
                Task task = getTask(scheduledEntity);
                schedule(task);
            }
        }
    }



    @SneakyThrows
    private Task getTask(TaskEntity entity) {
        Object data = getData(entity.getData(), Class.forName(entity.getDataClass()));
        Task task = new Task(entity.getPrincipal(),
                entity.getTaskExecutor(), entity.getDataClass(), data);
        task.setId(entity.getId());
        task.setTobeExecutedAt(entity.getTobeExecutedAt());
        if(null != entity.getGroup())
            task.setGroupId(entity.getGroup().getId());
        task.setTaskStage(entity.getStage());
        return task;
    }



    @Override
    public <T> void execute(String principal, Class<? extends AsyncConsumer<T>> clazz, T data) {
        Task task = new Task(principal, getSpringName(clazz), getClassName(data), data);
        RunnableTask runnableTask = new RunnableTask(task, ctx, onSyncTaskComplete);
        runnableTask.execute();
    }

    @SneakyThrows
    public boolean schedule(Task task) {
        boolean executeNow = false;
        if(canExecuteNow(task)) {
            executeNow = (executeNow(task));
        }
        if(!executeNow)
            return addSchedule(task);
        return false;
    }

    private static boolean canExecuteAfter(Task task, int minutes) {
        return null == task.getTobeExecutedAt()
                || task.getTobeExecutedAt().minus(minutes, ChronoUnit.MINUTES).isBefore(Instant.now());
    }

    private boolean addSchedule( Task task) {
        if(canExecuteAfter(task, 15)) {
            return queueManager.addScheduledTasks(task);
        }
        return false;
    }

    private boolean executeNow(Task task) {
        RunnableTask runnableTask = new RunnableTask(task, ctx, onAsyncTaskComplete);
        return queueManager.addToInflight(task, runnableTask);
    }



    private String writeData(Object data) {
        try {
            return mapper.writeValueAsString(data);
        } catch (JsonProcessingException jpe) {
            log.error("Error while serializing", jpe);
            throw new IllegalArgumentException("Data cannot be serialized into json", jpe);
        }
    }

    private <D> D getData(String data, Class<D> clazz) throws JsonProcessingException {
        try {
            return mapper.readValue(data, clazz);
        } catch (JsonProcessingException jpe) {
            log.error("Error while De-Serializing " + data, jpe);
            throw new IllegalArgumentException("Data cannot be de-serialized into json", jpe);
        }
    }

    public <T> String getSpringName(Class<? extends AsyncConsumer> clazz) {
        Component a = clazz.getAnnotation(Component.class);
        if (null == a || null == a.value()) {
            throw new IllegalArgumentException("Processing class " + clazz.getCanonicalName() + " must be annotated with Spring @Component(\"componentname\"");
        }
        return a.value();
    }

    public void shutdown() {
        taskExecutor.shutdown();
    }

    class AsyncOnComplete implements Consumer<Task> {
        @Override
        public void accept(Task task) {
            queueManager.removeInflightTask(task);
        }
    }
}
