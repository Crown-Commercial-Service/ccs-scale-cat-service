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
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import uk.gov.crowncommercial.dts.scale.cat.config.ApplicationFlagsConfig;
import uk.gov.crowncommercial.dts.scale.cat.config.ExperimentalFlagsConfig;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.Timestamps;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.TaskEntity;
import uk.gov.crowncommercial.dts.scale.cat.processors.async.AsyncConsumer;
import uk.gov.crowncommercial.dts.scale.cat.processors.async.AsyncExecutor;
import uk.gov.crowncommercial.dts.scale.cat.repo.TaskRepo;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

@Component
@Slf4j
@Scope("singleton")
public class QueuedAsyncExecutor implements AsyncExecutor {
    private final ThreadPoolTaskExecutor taskExecutor;
    private final ApplicationContext ctx;
    private final ApplicationFlagsConfig applicationFlags;
    private final ExperimentalFlagsConfig experimentalFlags;
    private final TaskEntityService taskEntityService;
    private final BlockingQueue<Runnable> queue;
    private final ArrayBlockingQueue<Task> task = new ArrayBlockingQueue<>(128);

    private final ObjectMapper mapper = new ObjectMapper();

    public QueuedAsyncExecutor(@Qualifier("comExecutor") ThreadPoolTaskExecutor executor, ApplicationContext ctx,
                               ApplicationFlagsConfig applicationFlags, ExperimentalFlagsConfig flags, TaskEntityService taskEntityService) {
        this.taskExecutor = executor;
        queue = executor.getThreadPoolExecutor().getQueue();
        this.ctx = ctx;
        this.experimentalFlags = flags;
        this.applicationFlags = applicationFlags;
        this.taskEntityService = taskEntityService;
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        mapper.registerModule(new JavaTimeModule());
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
            taskEntityService.persist(principal, task, recordType, recordId, writeData(task.getData()));
            schedule(task);
        } else {
            execute(principal, clazz, data);
        }
    }

    public void loadFromDataStore(List<TaskEntity> taskEntities) {
        for (TaskEntity taskEntity : taskEntities) {
            Task task = getTask(taskEntity);
            RunnableTask runnableTask = new RunnableTask(task, ctx);
            if (!inFlight(runnableTask)) {
                taskExecutor.execute(runnableTask);
            }
        }
    }

    private boolean inFlight(RunnableTask runnableTask) {
        return queue.contains(runnableTask);
    }

    @SneakyThrows
    private Task getTask(TaskEntity entity) {
        Object data = getData(entity.getData(), Class.forName(entity.getDataClass()));
        Task task = new Task(entity.getPrincipal(),
                entity.getTaskExecutor(), entity.getDataClass(), data);
        task.setId(entity.getId());
        task.setTaskStage(entity.getStage());
        return task;
    }



    @Override
    public <T> void execute(String principal, Class<? extends AsyncConsumer<T>> clazz, T data) {
        Task task = new Task(principal, getSpringName(clazz), getClassName(data), data);
        RunnableTask runnableTask = new RunnableTask(task, ctx);
        runnableTask.execute();
    }

    @SneakyThrows
    private void schedule(Task task) {
        RunnableTask runnableTask = new RunnableTask(task, ctx);
        taskExecutor.execute(runnableTask);
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
        Component a = (Component) clazz.getAnnotation(Component.class);
        if (null == a || null == a.value()) {
            throw new IllegalArgumentException("Processing class " + clazz.getCanonicalName() + " must be annotated with Spring @Component(\"componentname\"");
        }
        return a.value();
    }

    public void shutdown() {
        taskExecutor.shutdown();
    }
}
