package uk.gov.crowncommercial.dts.scale.cat.processors.async;

/**
 * Schedule independently executable processes with input and user principal
 */
public interface AsyncExecutor {
    public <T> void submit(String principal, Class<? extends AsyncConsumer<T>> clazz, T data);
}