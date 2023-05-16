package uk.gov.crowncommercial.dts.scale.cat.exception;

import java.util.Optional;

/**
 * Encapsulate details of a Jaggaer application exception
 */
public class DuplicateFiscalCodeException extends UpstreamServiceException {

  private static final String SERVICE_NAME = "Jaggaer";

  private static final long serialVersionUID = 1L;

  public DuplicateFiscalCodeException(final String message) {
    super(SERVICE_NAME, Optional.empty(), message);
  }
}