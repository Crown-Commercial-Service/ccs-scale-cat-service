package uk.gov.crowncommercial.dts.scale.cat.service.validators;

import org.springframework.stereotype.Component;

@Component
public class AlwaysFailChecker implements Validator{
    @Override
    public ErrorDetails getErrorMessage(Object data) {
        return new ErrorDetails("GENERIC", "test - always failed");
    }

    @Override
    public boolean test(Object o) {
        return false;
    }
}
