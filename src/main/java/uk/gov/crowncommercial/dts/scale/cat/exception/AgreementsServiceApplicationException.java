package uk.gov.crowncommercial.dts.scale.cat.exception;

import java.util.Optional;

/**
 * Agreements Service application exception
 */
public class AgreementsServiceApplicationException extends UpstreamServiceException {

  /**
   *
   */
  private static final long serialVersionUID = 1L;

  public AgreementsServiceApplicationException(final String msg) {
    super("Agreements Service", Optional.empty(), msg);
  }

}
