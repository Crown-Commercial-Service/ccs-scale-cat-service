package uk.gov.crowncommercial.dts.scale.cat.exception;

/**
 * Thrown when a principal's roles conflict in source systems. Should result in a 409 being
 * returned.
 */
public class UserRolesConflictException extends RuntimeException {

  /**
   *
   */
  private static final long serialVersionUID = 1L;

  public UserRolesConflictException(final String msg) {
    super(msg);
  }

}
