package uk.gov.crowncommercial.dts.scale.cat.controller;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.bind.ValidationException;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.exception.ResourceNotFoundException;
import uk.gov.crowncommercial.dts.scale.cat.model.ApiError;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.Errors;

/**
 * Centralised error handling for application and container derived error conditions.
 *
 * TODO: 401, 403 etc generated by Spring Security bypass error handling and send back error message
 * in WWW-Authenticate header, as per the spec. However, there is a means to customize the response
 * bodies for such exceptions (e.g. InvalidBearerTokenException) if we want to:
 * https://github.com/spring-projects/spring-security/issues/5985
 *
 */
@ControllerAdvice
@RestController
@Slf4j
public class GlobalErrorHandler implements ErrorController {

  private static final String ERR_MSG_DEFAULT = "An error occurred processing the request";
  private static final String ERR_MSG_UPSTREAM = "An error occurred invoking an upstream service";
  private static final String ERR_MSG_VALIDATION = "Validation error processing the request";
  private static final String ERR_MSG_RESOURCE_NOT_FOUND = "Resource not found";

  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ExceptionHandler({ValidationException.class, HttpMessageNotReadableException.class})
  public Errors handleValidationException(final Exception exception) {

    log.trace("Request validation exception", exception);

    var apiError = new ApiError(HttpStatus.BAD_REQUEST.toString(), ERR_MSG_RESOURCE_NOT_FOUND,
        exception.getMessage());
    return buildErrors(Arrays.asList(apiError));
  }

  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  @ExceptionHandler({WebClientResponseException.class})
  public Errors handleWebClientResponseException(final WebClientResponseException exception) {

    Optional<HttpRequest> request = Optional.ofNullable(exception.getRequest());

    var invokedService = "unknown";
    if (request.isPresent()) {
      invokedService = request.get().getMethod() + " " + request.get().getURI();
    }

    var logErrorMsg =
        String.format("Error invoking upstream service [%s], received status: [%d], body: [%s]",
            invokedService, exception.getRawStatusCode(), exception.getResponseBodyAsString());

    log.error(logErrorMsg, exception);

    return buildErrors(Arrays
        .asList(new ApiError(HttpStatus.INTERNAL_SERVER_ERROR.toString(), ERR_MSG_UPSTREAM, "")));
  }

  @ResponseStatus(HttpStatus.NOT_FOUND)
  @ExceptionHandler({ResourceNotFoundException.class})
  public Errors handleResourceNotFoundException(final ResourceNotFoundException exception) {

    log.info("Requested resource not found", exception);

    var apiError =
        new ApiError(HttpStatus.NOT_FOUND.toString(), ERR_MSG_VALIDATION, exception.getMessage());
    return buildErrors(Arrays.asList(apiError));
  }

  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  @ExceptionHandler(Exception.class)
  public Errors handleUnknownException(final Exception exception) {

    log.error("Unknown application exception", exception);

    var apiError = new ApiError(HttpStatus.INTERNAL_SERVER_ERROR.toString(), ERR_MSG_DEFAULT, "");
    return buildErrors(Arrays.asList(apiError));
  }

  @RequestMapping("/error")
  public ResponseEntity<Errors> handleError(final HttpServletRequest request,
      final HttpServletResponse response) {

    Object exception = request.getAttribute("javax.servlet.error.exception");

    log.error("Unknown container/filter exception", exception);

    return ResponseEntity.badRequest().body(buildErrors(Arrays
        .asList(new ApiError(HttpStatus.INTERNAL_SERVER_ERROR.toString(), ERR_MSG_DEFAULT, ""))));
  }

  @Override
  public String getErrorPath() {
    return "/error";
  }

  private Errors buildErrors(List<ApiError> apiErrors) {
    var errors = new Errors();
    errors.setErrors(apiErrors);
    return errors;
  }

}
