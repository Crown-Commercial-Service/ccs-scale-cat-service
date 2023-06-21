package uk.gov.crowncommercial.dts.scale.cat.processors.async.queueExecutor;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.*;
import java.util.concurrent.Executor;

@RequiredArgsConstructor
public class QueueManager {
    private final List<Task> scheduledTasks = new ArrayList<>(16);
    private final Set<Integer> inflightGroups = new HashSet<>();
    private final List<Task> inflightTasks = new ArrayList<>(16);
    private final Executor executor;


    public boolean removeInflightTask(Task task){
        Integer groupId = task.getGroupId();
        boolean result = inflightTasks.remove(task);
        if(!hasGroupInFlight(inflightTasks, groupId))
            inflightGroups.remove(groupId);
        return result;
    }

    private boolean hasGroupInFlight(Integer groupId){
        return inflightGroups.contains(groupId);
    }

    private boolean hasGroupInFlight(List<Task> inflightTasks, Integer groupId) {
        return inflightTasks.stream().anyMatch(t -> t.getGroupId() == groupId);
    }

    public List<Task> getScheduledTasks(){
        return Collections.unmodifiableList(scheduledTasks);
    }

    public int scheduleSize() {
        return scheduledTasks.size();
    }

    public boolean addScheduledTasks(Task task){
        if (!inQueue(task)) {
            scheduledTasks.add(task);
            return true;
        }
        return false;
    }

    private boolean inQueue(Task task){
        return scheduledTasks.contains(task) || inflightTasks.contains(task);
    }

    public boolean addToInflight(Task task, Runnable runnable) {
        if(ifExecQueueFull())
            return false;
        Integer groupId = task.getGroupId();
        if(!hasGroupInFlight(groupId)) {
            if (!inflightTasks.contains(task)) {
                inflightGroups.add(groupId);
                scheduledTasks.remove(task);
                inflightTasks.add(task);
                executor.execute(runnable);
                return true;
            }
        }
        return false;
    }

    private boolean ifExecQueueFull(){
        return inflightTasks.size() > AsyncExecutionConfig.poolSize * 2;
    }

    public boolean queueFull(){
        return (scheduledTasks.size() + inflightTasks.size()) > 64;
    }
}
