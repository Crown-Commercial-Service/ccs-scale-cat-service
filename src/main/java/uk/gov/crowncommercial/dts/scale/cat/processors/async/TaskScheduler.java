package uk.gov.crowncommercial.dts.scale.cat.processors.async;

import uk.gov.crowncommercial.dts.scale.cat.processors.async.queueExecutor.Task;

public interface TaskScheduler {
    public boolean schedule(Task task);
}
