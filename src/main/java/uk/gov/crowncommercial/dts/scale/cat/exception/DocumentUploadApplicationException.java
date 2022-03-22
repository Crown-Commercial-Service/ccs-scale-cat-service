package uk.gov.crowncommercial.dts.scale.cat.exception;

import java.util.Optional;

/**
 * Encapsulate details of a Jaggaer application exception (a 200 OK response with error code and
 * message, such as when an invalid project template code is provided in create project)
 */
public class DocumentUploadApplicationException extends UpstreamServiceException {

  private static final String SERVICE_NAME = "Document Upload Service";
  /**
   *
   */
  private static final long serialVersionUID = 1L;

  public DocumentUploadApplicationException(final Object code, final String message) {
    super(SERVICE_NAME, Optional.of(code), message);
  }

  public DocumentUploadApplicationException(final String message) {
    super(SERVICE_NAME, Optional.empty(), message);
  }
}
