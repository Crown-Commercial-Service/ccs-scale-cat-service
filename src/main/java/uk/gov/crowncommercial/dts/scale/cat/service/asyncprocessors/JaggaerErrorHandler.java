package uk.gov.crowncommercial.dts.scale.cat.service.asyncprocessors;

import uk.gov.crowncommercial.dts.scale.cat.exception.JaggaerApplicationException;
import uk.gov.crowncommercial.dts.scale.cat.processors.async.ErrorHandler;
import uk.gov.crowncommercial.dts.scale.cat.processors.async.NoopErrorHandler;

public class JaggaerErrorHandler implements ErrorHandler {
    public static final JaggaerErrorHandler INSTANCE = new JaggaerErrorHandler();
    @Override
    public String getMessage(Throwable t) {
        return t.getMessage();
    }

    @Override
    public boolean canRetry(Throwable t) {
        JaggaerApplicationException jae = (JaggaerApplicationException)t;
        if(jae.getMessage().contains("Code: [-998]")){
            return true;
        }else
            return false;
    }

    @Override
    public boolean canHandle(Throwable t) {
        return t instanceof JaggaerApplicationException;
    }
}
