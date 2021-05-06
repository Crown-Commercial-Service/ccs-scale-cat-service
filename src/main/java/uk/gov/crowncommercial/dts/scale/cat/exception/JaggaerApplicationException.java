package uk.gov.crowncommercial.dts.scale.cat.exception;

/**
 * Encapsulate details of a Jaggaer application exception (a 200 OK response with error code and
 * message, such as when an invalid project template code is provided in create project)
 */
public class JaggaerApplicationException extends RuntimeException {

  static final String ERR_MSG_TEMPLATE = "Jaggaer application exception, Code: [%d], Message: [%s]";

  /**
   *
   */
  private static final long serialVersionUID = 1L;

  public JaggaerApplicationException(int code, String message) {
    super(String.format(ERR_MSG_TEMPLATE, code, message));
  }

}
