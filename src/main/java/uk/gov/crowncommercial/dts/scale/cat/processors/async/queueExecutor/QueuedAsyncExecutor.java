package uk.gov.crowncommercial.dts.scale.cat.processors.async.queueExecutor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import uk.gov.crowncommercial.dts.scale.cat.processors.async.AsyncConsumer;
import uk.gov.crowncommercial.dts.scale.cat.processors.async.AsyncExecutor;

import java.lang.annotation.Annotation;

@Component
@Slf4j
public class QueuedAsyncExecutor implements AsyncExecutor {
    private final ThreadPoolTaskExecutor taskExecutor;
    private final ApplicationContext ctx;

    private final ObjectMapper mapper= new ObjectMapper();

    public QueuedAsyncExecutor(@Qualifier("comExecutor") ThreadPoolTaskExecutor executor, ApplicationContext ctx) {
        this.taskExecutor = executor;
        this.ctx = ctx;
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    }

    public void startup() {

    }

    private String getClassName(Object data) {
        if (null != data) {
            return data.getClass().getCanonicalName();
        }
        return null;
    }


    public void submit(String principal, String Runner, Object data) {
        Task task = new Task(principal, Runner, getClassName(data), data);
        schedule(task);
    }


    @Override
    public <T> void submit(String principal, Class<? extends AsyncConsumer<T>> clazz, T data) {
        Task task = new Task(principal, getSpringName(clazz), getClassName(data), data);
        schedule(task);
    }

    @SneakyThrows
    private void schedule(Task task) {
        String sdata = writeData(task.getData());
        Object writeData = getData(sdata, Class.forName(task.getClassName()));
        task.setData(writeData);
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
            log.error("Error while De-Serializing " + data,  jpe);
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
