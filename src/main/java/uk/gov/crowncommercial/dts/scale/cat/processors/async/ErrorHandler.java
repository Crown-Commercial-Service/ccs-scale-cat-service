package uk.gov.crowncommercial.dts.scale.cat.processors.async;

public interface ErrorHandler {
    String getMessage(Throwable t);

    boolean canRetry(Throwable t);

    boolean canHandle(Throwable t);
}
