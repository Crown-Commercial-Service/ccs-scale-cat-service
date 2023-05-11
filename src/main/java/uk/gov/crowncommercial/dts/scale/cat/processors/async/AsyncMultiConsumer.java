package uk.gov.crowncommercial.dts.scale.cat.processors.async;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.*;

public abstract class AsyncMultiConsumer<T> implements AsyncConsumer<T>{

    ArrayList<Entry<TaskConsumer<T>>> consumers = new ArrayList<>();
    public final String accept(String principal, T data){
        throw new UnsupportedOperationException("This accept method should not be invoked");
    }

    protected final void addConsumers(String key, TaskConsumer<T> consumer){
        consumers.add(new Entry(key, consumer));
    }

    public TaskConsumer<T> getTaskConsumer(String key){
        for(Entry e : consumers){
            if(e.getKey().equalsIgnoreCase(key)){
                return e.getConsumer();
            }
        }
        return null;
    }

    public List<String> getAllTasks(){
        List<String> keys = new ArrayList<>();
        for(Entry e : consumers){
            keys.add(e.getKey());
        }
        return keys;
    }

    public TaskConsumer<T> getFirstConsumer(){
        return consumers.get(0).getConsumer();
    }

    public int getTaskIndex(String key){
        int i = -1;
        for(Entry e : consumers){
            i++;
            if(e.getKey().equalsIgnoreCase(key)){
                return i;
            }
        }
        return -1;
    }
}

@RequiredArgsConstructor
@Getter
class Entry<C extends TaskConsumer>{
    private final String key;
    private final C consumer;
}