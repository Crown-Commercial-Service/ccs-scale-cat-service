package uk.gov.crowncommercial.dts.scale.cat.exception;

/**
 * Catch all exception for Login Director endpoint edge case scenario(s) (e.g. CON-1682 AC15)
 */
public class LoginDirectorEdgeCaseException extends RuntimeException {

  /**
   *
   */
  private static final long serialVersionUID = 1L;

  public LoginDirectorEdgeCaseException(final String msg) {
    super(msg);
  }

}
