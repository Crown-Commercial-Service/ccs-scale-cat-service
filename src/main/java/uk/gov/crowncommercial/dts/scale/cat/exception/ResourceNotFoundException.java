package uk.gov.crowncommercial.dts.scale.cat.exception;

/**
 * Client-requested resource (database or external) cannot be located. Should be handled and result
 * in a 404 being returned.
 */
public class ResourceNotFoundException extends RuntimeException {

  /**
   *
   */
  private static final long serialVersionUID = 1L;

  public ResourceNotFoundException(final String msg) {
    super(msg);
  }

}
