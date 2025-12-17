package uk.gov.crowncommercial.dts.scale.cat.exception;

import java.util.Optional;

/**
 * Stage Service application exception
 */
public class StageException extends UpstreamServiceException {
  /**
   *
   */
  private static final long serialVersionUID = 1L;

  public StageException(final String msg) {
    super("Stage Service", Optional.empty(), msg);
  }
}
