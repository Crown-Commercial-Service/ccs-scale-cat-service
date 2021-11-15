package uk.gov.crowncommercial.dts.scale.cat.exception;

import java.util.Optional;

/**
 * Conclave application exception
 */
public class ConclaveApplicationException extends UpstreamServiceException {

  /**
   *
   */
  private static final long serialVersionUID = 1L;

  public ConclaveApplicationException(final String msg) {
    super("Conclave", Optional.empty(), msg);
  }

}
