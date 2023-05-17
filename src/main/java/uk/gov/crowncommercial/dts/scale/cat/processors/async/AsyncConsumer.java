package uk.gov.crowncommercial.dts.scale.cat.processors.async;

public interface AsyncConsumer<T> extends TaskConsumer<T>{

    String getIdentifier(T data);

    default  void onScheduled(String principal, T data){
    }

    default void onStart(String principal, T data){
    }

    default void onSuccess(String principal, T data){
    }

    default void onError(String principal, T data){
    }

    default void onMarkedRetry(String principal, T data){

    }

    default void onStatusChange(String principal, T data, AsyncTaskStatus taskStatus){
        switch (taskStatus){
            case SCHEDULED -> onScheduled(principal, data);
            case FAILED -> onError(principal, data);
            case IN_FLIGHT -> onStart(principal, data);
            case COMPLETED -> onSuccess(principal, data);
            case RETRY -> onMarkedRetry(principal, data);
        }
    }
}
