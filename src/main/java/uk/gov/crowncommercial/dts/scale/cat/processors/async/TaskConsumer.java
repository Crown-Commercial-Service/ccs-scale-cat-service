package uk.gov.crowncommercial.dts.scale.cat.processors.async;

import java.util.List;

public interface TaskConsumer<T> {
    String accept(String principal, T data);

    List<ErrorHandler> getErrorHandlers();

    String getTaskName();

    default String getTaskCode(){
        return getTaskName();
    }
}
