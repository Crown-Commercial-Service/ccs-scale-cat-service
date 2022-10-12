package uk.gov.crowncommercial.dts.scale.cat.exception;

/**
 * Thrown when JaggaerUserExist. Should result in a 409 being
 * returned.
 */
public class JaggaerUserNotExistException extends RuntimeException {

  /**
   *
   */
  private static final long serialVersionUID = 1L;

  public JaggaerUserNotExistException(final String msg) {
    super(msg);
  }

}
