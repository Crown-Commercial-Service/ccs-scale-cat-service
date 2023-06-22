package uk.gov.crowncommercial.dts.scale.cat.processors.async;

public interface AsyncConsumer<T> {

    String accept(String principal, T data);

    default String onError(String errorCode, Throwable cause){
        if(null != cause)
                return "Error " + errorCode + ":" + cause.getMessage() + " is not handled";
        else
            return "Error " + errorCode + " is not handled";
    }

    default boolean canRetry(String errorCode, RetryableException re){
        return false;
    }

    String getIdentifier(T data);

    String getTaskName();
}
