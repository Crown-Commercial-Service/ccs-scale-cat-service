package uk.gov.crowncommercial.dts.scale.cat.exception;

/**
 * Malformed JSON Web Token exception
 */
public class MalformedJwtException extends RuntimeException {

  /**
  *
  */
  private static final long serialVersionUID = 1L;

  public MalformedJwtException(final String msg) {
    super(msg);
  }

}
