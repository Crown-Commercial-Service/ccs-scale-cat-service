package uk.gov.crowncommercial.dts.scale.cat.service.asyncprocessors;

import uk.gov.crowncommercial.dts.scale.cat.processors.async.ErrorHandler;
import uk.gov.crowncommercial.dts.scale.cat.processors.async.NoopErrorHandler;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class ErrorHandlers {
    public static final List<ErrorHandler> jaggaerHandlers = Collections.unmodifiableList(Arrays.asList(NoopErrorHandler.INSTANCE));

}
