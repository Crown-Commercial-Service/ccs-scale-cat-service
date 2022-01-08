package uk.gov.crowncommercial.dts.scale.cat.exception;

import java.util.Optional;

/**
 * Base class for all upstream (AS, Jaggaer, Conclave) service errors
 */
public abstract class UpstreamServiceException extends RuntimeException {

  static final String ERR_MSG_TEMPLATE = "%s application exception, Code: [%s], Message: [%s]";

  /**
   *
   */
  private static final long serialVersionUID = 1L;

  protected UpstreamServiceException(final String serviceName, final Optional<Object> code,
      final String message) {
    super(String.format(ERR_MSG_TEMPLATE, serviceName, code.orElseGet(() -> "N/A"), message));
  }

}
