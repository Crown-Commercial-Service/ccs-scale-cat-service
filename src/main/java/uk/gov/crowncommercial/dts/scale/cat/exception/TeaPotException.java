package uk.gov.crowncommercial.dts.scale.cat.exception;

/**
 * Thrown when a principal's roles conflict in source systems. Should result in a 418 being
 * returned.
 */
public class TeaPotException extends RuntimeException {

  /**
   *
   */
  private static final long serialVersionUID = 1L;

  public TeaPotException(final String msg) {
    super(msg);
  }

}
