package uk.gov.crowncommercial.dts.scale.cat.processors.async;

/**
 * Schedule independently executable processes with input and user principal
 */
public interface AsyncExecutor {
    /**
     * Submits the task for execution for later execution
     * @param principal
     * @param clazz
     * @param data
     * @param recordType
     * @param recordId
     * @param <T>
     */
    public <T> void submit(String principal, Class<? extends AsyncConsumer<T>> clazz, T data, String recordType, String recordId);

    /**
     * Executes the task immediately in the same thread
     *
     * @param principal
     * @param clazz
     * @param data
     * @param <T>
     */
    public <T> void execute(String principal, Class<? extends AsyncConsumer<T>> clazz, T data);
}