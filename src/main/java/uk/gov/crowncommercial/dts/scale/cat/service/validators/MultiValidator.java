package uk.gov.crowncommercial.dts.scale.cat.service.validators;

import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;

import java.util.List;

public class MultiValidator<T> implements Validator<T> {
    List<Validator<T>> validators;
    private MultiValidator(){

    }

    public static <T> MultiValidator<T> of(List<Validator<T>> validators){
        MultiValidator validator = new MultiValidator();
        validator.validators = validators;
        return validator;
    }

    @Override
    public ErrorDetails getErrorMessage(T data) {
        throw new UnsupportedOperationException("This method not implemented");
    }

    @Override
    public boolean test(T data) {
        Validator<T> t;
        for(Validator<T> v: validators){
            if(!v.test(data)){
                v.throwException(data);
            }
        }
        return true;
    }
}
