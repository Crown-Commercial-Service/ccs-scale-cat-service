package uk.gov.crowncommercial.dts.scale.cat.exception;

import java.util.Optional;

/**
 * Encapsulate details of a Jaggaer RPA application exception
 */
public class JaggaerRPAException extends UpstreamServiceException {

  private static final String SERVICE_NAME = "Jaggaer RPA";

  private static final long serialVersionUID = 1L;

  public JaggaerRPAException(final Object code, final String message) {
    super(SERVICE_NAME, Optional.of(code), message);
  }

  public JaggaerRPAException(final String message) {
    super(SERVICE_NAME, Optional.empty(), message);
  }
}