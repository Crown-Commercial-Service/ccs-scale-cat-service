package uk.gov.crowncommercial.dts.scale.cat.exception;

/**
 * General purpose exception to be thrown by the application's business logic code when an
 * unauthorised operation is attempted, for example getting role information for a different user to
 * the currently authenticated one. Should result in a 403 being returned.
 */
public class AuthorisationFailureException extends RuntimeException {

  /**
   *
   */
  private static final long serialVersionUID = 1L;

  public AuthorisationFailureException(final String msg) {
    super(msg);
  }

}
