package uk.gov.crowncommercial.dts.scale.cat.controller;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import java.util.Arrays;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.bind.ValidationException;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.retry.ExhaustedRetryException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.config.Constants;
import uk.gov.crowncommercial.dts.scale.cat.config.OAuth2Config;
import uk.gov.crowncommercial.dts.scale.cat.exception.JaggaerApplicationException;
import uk.gov.crowncommercial.dts.scale.cat.exception.MalformedJwtException;
import uk.gov.crowncommercial.dts.scale.cat.exception.ResourceNotFoundException;
import uk.gov.crowncommercial.dts.scale.cat.model.ApiError;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.Errors;
import uk.gov.crowncommercial.dts.scale.cat.utils.TendersAPIModelUtils;

/**
 * Centralised error handling for application and container derived error conditions.
 *
 * @see {@link OAuth2Config} for 401/403 security error handling configuration.
 *
 */
@ControllerAdvice
@RestController
@RequiredArgsConstructor
@Slf4j
public class GlobalErrorHandler implements ErrorController {

  private static final String ERR_MSG_DEFAULT = "An error occurred processing the request";
  private static final String ERR_MSG_UPSTREAM = "An error occurred invoking an upstream service";
  private static final String ERR_MSG_VALIDATION = "Validation error processing the request";
  private static final String ERR_MSG_RESOURCE_NOT_FOUND = "Resource not found";

  private final TendersAPIModelUtils tendersAPIModelUtils;

  @ResponseStatus(BAD_REQUEST)
  @ExceptionHandler({ValidationException.class, HttpMessageNotReadableException.class,
      IllegalArgumentException.class})
  public Errors handleValidationException(final Exception exception) {

    log.trace("Request validation exception", exception);

    var apiError =
        new ApiError(BAD_REQUEST.toString(), ERR_MSG_RESOURCE_NOT_FOUND, exception.getMessage());
    return tendersAPIModelUtils.buildErrors(Arrays.asList(apiError));
  }

  @ResponseStatus(INTERNAL_SERVER_ERROR)
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

    return tendersAPIModelUtils.buildErrors(
        Arrays.asList(new ApiError(INTERNAL_SERVER_ERROR.toString(), ERR_MSG_UPSTREAM, "")));
  }

  @ResponseStatus(NOT_FOUND)
  @ExceptionHandler({ResourceNotFoundException.class})
  public Errors handleResourceNotFoundException(final ResourceNotFoundException exception) {

    log.info("Requested resource not found", exception);

    var apiError = new ApiError(NOT_FOUND.toString(), ERR_MSG_VALIDATION, exception.getMessage());
    return tendersAPIModelUtils.buildErrors(Arrays.asList(apiError));
  }

  @ResponseStatus(INTERNAL_SERVER_ERROR)
  @ExceptionHandler(JaggaerApplicationException.class)
  public Errors handleJaggaerApplicationException(final JaggaerApplicationException exception) {

    log.error("Jaggaer application error", exception);

    var apiError = new ApiError(INTERNAL_SERVER_ERROR.toString(), ERR_MSG_UPSTREAM, "");
    return tendersAPIModelUtils.buildErrors(Arrays.asList(apiError));
  }

  @ResponseStatus(INTERNAL_SERVER_ERROR)
  @ExceptionHandler(ExhaustedRetryException.class)
  public Errors handleExhaustedRetryException(final ExhaustedRetryException exception) {

    log.error("Exhausted retries", exception);

    var apiError = new ApiError(INTERNAL_SERVER_ERROR.toString(), ERR_MSG_UPSTREAM, "");
    return tendersAPIModelUtils.buildErrors(Arrays.asList(apiError));
  }

  @ResponseStatus(UNAUTHORIZED)
  @ExceptionHandler(MalformedJwtException.class)
  public Errors handleMalformedJwtException(final MalformedJwtException exception) {

    log.error("MalformedJwtException", exception);

    var apiError = new ApiError(UNAUTHORIZED.toString(), Constants.ERR_MSG_INVALID_JWT, "");
    return tendersAPIModelUtils.buildErrors(Arrays.asList(apiError));
  }

  @ResponseStatus(INTERNAL_SERVER_ERROR)
  @ExceptionHandler(Exception.class)
  public Errors handleUnknownException(final Exception exception) {

    log.error("Unknown application exception", exception);

    var apiError = new ApiError(INTERNAL_SERVER_ERROR.toString(), ERR_MSG_DEFAULT, "");
    return tendersAPIModelUtils.buildErrors(Arrays.asList(apiError));
  }

  @RequestMapping("/error")
  public ResponseEntity<Errors> handleError(final HttpServletRequest request,
      final HttpServletResponse response) {

    var exception = (Throwable) request.getAttribute("javax.servlet.error.exception");

    log.error("Unknown container/filter exception", exception);

    return ResponseEntity.status(INTERNAL_SERVER_ERROR).body(tendersAPIModelUtils.buildErrors(
        Arrays.asList(new ApiError(INTERNAL_SERVER_ERROR.toString(), ERR_MSG_DEFAULT, ""))));
  }

  @Override
  public String getErrorPath() {
    return "/error";
  }

}
