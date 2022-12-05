package uk.gov.crowncommercial.dts.scale.cat.processors.async;

public class RetryableException extends RuntimeException{
    private final String errorCode;

    public RetryableException(String errorCode, String message){
        super(message);
        this.errorCode = errorCode;
    }

    public RetryableException(String errorCode, String message, Throwable cause){
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode(){
        return errorCode;
    }
}
