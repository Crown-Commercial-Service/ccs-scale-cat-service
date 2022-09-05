package uk.gov.crowncommercial.dts.scale.cat.exception;

/**
 * Class for not allowed action
 */
public class OperationNotSupportedException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public OperationNotSupportedException(final String msg) {
    super(msg);
  }
}
