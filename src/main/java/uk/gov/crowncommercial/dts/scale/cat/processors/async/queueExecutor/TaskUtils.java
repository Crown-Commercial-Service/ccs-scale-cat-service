package uk.gov.crowncommercial.dts.scale.cat.processors.async.queueExecutor;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import uk.gov.crowncommercial.dts.scale.cat.config.EnvironmentConfig;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.TaskEntity;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.TaskHistoryEntity;

import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class TaskUtils {
    private final EnvironmentConfig environmentConfig;
    public boolean isSchedulable(TaskEntity entity){
        if(entity.getStatus() == Task.SCHEDULED &&
                (null == entity.getNode() || entity.getNode().equalsIgnoreCase(getNodeName()))){
            return true;
        }
        return false;
    }

    public String getNodeName(){
        return environmentConfig.getServiceInstance();
    }

    public static void sleep(int i) {
        try {
            Thread.sleep(i);
        } catch (InterruptedException e) {
        }
    }

    public static boolean isClosed(TaskHistoryEntity entity){
        char status = entity.getStatus();
        return (status == 'C' || status == 'F' || status == 'A');
    }

    public static <T> T get(Supplier<T> supplier, int retry){
        T data = null;
        int i = 0;
        do{
            sleep(1000);
            i++;
            data = supplier.get();
        } while (null == data && i <= retry);
        return data;
    }
}
