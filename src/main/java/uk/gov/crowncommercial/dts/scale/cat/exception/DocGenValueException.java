package uk.gov.crowncommercial.dts.scale.cat.exception;

/**
 * Wraps exceptions thrown when retrieving values as part of document generation
 */
public class DocGenValueException extends RuntimeException {

  /**
   *
   */
  private static final long serialVersionUID = 1L;

  public DocGenValueException(final Throwable cause) {
    super(cause);
  }

}
