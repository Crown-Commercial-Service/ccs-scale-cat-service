package uk.gov.crowncommercial.dts.scale.cat.exception;

/**
 * Class for not allowed action
 */
public class NotSupportedException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public NotSupportedException(final String msg) {
    super(msg);
  }
}
