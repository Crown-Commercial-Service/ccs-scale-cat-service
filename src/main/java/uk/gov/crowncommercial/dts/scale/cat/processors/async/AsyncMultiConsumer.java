package uk.gov.crowncommercial.dts.scale.cat.processors.async;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.*;

public abstract class AsyncMultiConsumer<T> implements AsyncConsumer<T>{

    private List<TaskConsumer<T>> consumers;
    public final String accept(String principal, T data){
        throw new UnsupportedOperationException("This accept method should not be invoked");
    }

    public TaskConsumer<T> getTaskConsumer(String key){
        for(TaskConsumer e : consumerList()){
            if(e.getTaskCode().equalsIgnoreCase(key)){
                return e;
            }
        }
        return null;
    }

    public List<String> getAllTasks(){
        List<String> keys = new ArrayList<>();
        for(TaskConsumer e : consumerList()){
            keys.add(e.getTaskCode());
        }
        return keys;
    }

    public int getTaskIndex(String key){
        if(null == key)
            return 0;
        int i = -1;
        for(TaskConsumer e : consumerList()){
            i++;
            if(e.getTaskCode().equalsIgnoreCase(key)){
                return i;
            }
        }
        return 0;
    }

    private List<TaskConsumer<T>> consumerList(){
        if(null == consumers)
            consumers = getConsumers();
        return consumers;
    }

    protected abstract List<TaskConsumer<T>> getConsumers();
}
