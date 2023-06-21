package uk.gov.crowncommercial.dts.scale.cat.service.validators;

import uk.gov.crowncommercial.dts.scale.cat.exception.PreConditionFailedException;

import java.util.function.Predicate;

public interface Validator<T> extends Predicate<T> {
    default void throwException(T data){
        ErrorDetails error = getErrorMessage(data);
        throw new PreConditionFailedException(error.getErrorCode(), error.getMessage());
    }

    ErrorDetails getErrorMessage(T data);
}
