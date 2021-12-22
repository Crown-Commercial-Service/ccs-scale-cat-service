package uk.gov.crowncommercial.dts.scale.cat.exception;

/**
 * Thrown when a principal exists with matching roles in both Conclave and Jaggaer but the user has
 * not been merged, i.e. no SSO data is present on their profile. Should result in a 409 being
 * returned.
 */
public class UnmergedJaggaerUserException extends UserRolesConflictException {

  /**
   *
   */
  private static final long serialVersionUID = 1L;

  public UnmergedJaggaerUserException(final String msg) {
    super(msg);
  }

}
