package uk.gov.crowncommercial.dts.scale.cat.processors.async;

public class NoopErrorHandler implements ErrorHandler{

    public static final NoopErrorHandler INSTANCE = new NoopErrorHandler();

    @Override
    public String getMessage(Throwable cause) {
        return cause.getMessage();
    }

    @Override
    public boolean canRetry(Throwable t) {
        return true;
    }

    @Override
    public boolean canHandle(Throwable t) {
        return true;
    }

}
