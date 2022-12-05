package uk.gov.crowncommercial.dts.scale.cat.processors.async;

public interface AsyncConsumer<T> {

    void accept(String principal, T data);

    void onError(String errorCode, Throwable cause);

    default boolean canRetry(String errorCode, RetryableException re){
        return false;
    }

    String getTaskName();
}
