package uk.gov.crowncommercial.dts.scale.cat.exception;

/**
 * General purpose data conflict exception (e.g. resource being created already exsts)
 */
public class DataConflictException extends RuntimeException {

  /**
   *
   */
  private static final long serialVersionUID = 1L;

  public DataConflictException(final String msg) {
    super(msg);
  }

}
