package uk.gov.crowncommercial.dts.scale.cat.service.ca;

/**
 * Catch-all exception type for CA calc related exceptions
 */
public class CAException extends RuntimeException {

  /**
   *
   */
  private static final long serialVersionUID = 1L;

  public CAException(final String msg) {
    super(msg);
  }

  public CAException(final String msg, final Throwable cause) {
    super(msg, cause);
  }

}
