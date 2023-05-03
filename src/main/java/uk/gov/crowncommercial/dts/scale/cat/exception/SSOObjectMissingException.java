package uk.gov.crowncommercial.dts.scale.cat.exception;

public class SSOObjectMissingException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SSOObjectMissingException(final String msg) {
        super(msg);
    }
}
