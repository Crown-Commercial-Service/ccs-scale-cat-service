package uk.gov.crowncommercial.dts.scale.cat.exception;

import lombok.Getter;

@Getter
public class PreConditionFailedException extends RuntimeException{
    private String errorCode;
    public PreConditionFailedException(String errorCode, String message){
        super(message);
        this.errorCode = errorCode;
    }
}
