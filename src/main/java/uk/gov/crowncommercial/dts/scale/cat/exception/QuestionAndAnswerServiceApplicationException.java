package uk.gov.crowncommercial.dts.scale.cat.exception;

import java.util.Optional;

/**
 * Question and Answer Service application exception
 */
public class QuestionAndAnswerServiceApplicationException extends UpstreamServiceException {

  /**
   *
   */
  private static final long serialVersionUID = 1L;

  public QuestionAndAnswerServiceApplicationException(final String msg) {
    super("Question and Answer Service", Optional.empty(), msg);
  }

}
