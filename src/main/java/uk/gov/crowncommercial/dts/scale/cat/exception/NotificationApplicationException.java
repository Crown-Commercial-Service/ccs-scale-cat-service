package uk.gov.crowncommercial.dts.scale.cat.exception;

import java.util.Optional;

/**
 *
 */
public class NotificationApplicationException extends UpstreamServiceException {

  private static final String SERVICE_NAME = "GOV UK Notify";
  /**
   *
   */
  private static final long serialVersionUID = 1L;

  public NotificationApplicationException(final Object code, final String message) {
    super(SERVICE_NAME, Optional.of(code), message);
  }

}
