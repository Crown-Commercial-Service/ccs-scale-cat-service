package uk.gov.crowncommercial.dts.scale.cat.exception;

public class SupplierNotMatchException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SupplierNotMatchException(final String msg) {
        super(msg);
    }
}
