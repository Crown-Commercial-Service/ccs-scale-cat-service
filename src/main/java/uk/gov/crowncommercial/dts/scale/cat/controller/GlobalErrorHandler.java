package uk.gov.crowncommercial.dts.scale.cat.controller;

import static org.springframework.http.HttpStatus.*;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ValidationException;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.retry.ExhaustedRetryException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import com.google.common.net.HttpHeaders;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.config.ApplicationFlagsConfig;
import uk.gov.crowncommercial.dts.scale.cat.config.Constants;
import uk.gov.crowncommercial.dts.scale.cat.config.OAuth2Config;
import uk.gov.crowncommercial.dts.scale.cat.exception.*;
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

  private final TendersAPIModelUtils tendersAPIModelUtils;
  private final ApplicationFlagsConfig appFlagsConfig;

  @RequestMapping("/error")
  public ResponseEntity<Errors> handleError(final HttpServletRequest request,
      final HttpServletResponse response) {

    var exception = (Throwable) request.getAttribute("jakarta.servlet.error.exception");

    log.error("Unknown container/filter exception", exception);

    return ResponseEntity.status(INTERNAL_SERVER_ERROR)
        .body(tendersAPIModelUtils.buildDefaultErrors(INTERNAL_SERVER_ERROR.toString(),
            Constants.ERR_MSG_DEFAULT, Objects.isNull(exception) ? "" : exception.getMessage()));
  }

  @ResponseStatus(INTERNAL_SERVER_ERROR)
  @ExceptionHandler(ExhaustedRetryException.class)
  public Errors handleExhaustedRetryException(final ExhaustedRetryException exception) {

    log.error("Exhausted retries", exception);

    return tendersAPIModelUtils.buildDefaultErrors(INTERNAL_SERVER_ERROR.toString(),
        Constants.ERR_MSG_UPSTREAM, exception.getMessage());
  }

  @ResponseStatus(UNAUTHORIZED)
  @ExceptionHandler(MalformedJwtException.class)
  public ResponseEntity<Errors> handleMalformedJwtException(final MalformedJwtException exception) {

    log.error("MalformedJwtException", exception);

    var apiError = new ApiError(UNAUTHORIZED.toString(), Constants.ERR_MSG_UNAUTHORISED, "");
    var wwwAuthenticateBearerInvalidJWT =
        "Bearer error=\"invalid_token\", error_description=\"Invalid or malformed JWT\"";
    return ResponseEntity.status(UNAUTHORIZED)
        .header(HttpHeaders.WWW_AUTHENTICATE, wwwAuthenticateBearerInvalidJWT)
        .body(tendersAPIModelUtils.buildErrors(Arrays.asList(apiError)));
  }

  @ResponseStatus(FORBIDDEN)
  @ExceptionHandler(AuthorisationFailureException.class)
  public Errors handleUnauthorisedException(final AuthorisationFailureException exception) {

    log.error("UnauthorisedException", exception);

    var apiError = new ApiError(FORBIDDEN.toString(), exception.getMessage(),
        appFlagsConfig.getDevMode() != null && appFlagsConfig.getDevMode() ? exception.getMessage()
            : "");
    return tendersAPIModelUtils.buildErrors(Arrays.asList(apiError));
  }

  @ResponseStatus(CONFLICT)
  @ExceptionHandler({UserRolesConflictException.class})
  public Errors handleUserRolesConflictException(final UserRolesConflictException exception) {

    log.debug("Profile management conflict exception (" + exception.getClass().getName() + "): "
        + exception.getMessage());

    return tendersAPIModelUtils
        .buildErrors(Arrays.asList(new ApiError(CONFLICT.toString(), exception.getMessage(), "")));
  }

  @ResponseStatus(CONFLICT)
  @ExceptionHandler({DataConflictException.class})
  public Errors handleDataConflictException(final DataConflictException exception) {

    log.warn("Data conflict exception", exception);

    return tendersAPIModelUtils.buildErrors(
        Arrays.asList(new ApiError(CONFLICT.toString(), "Data conflict", exception.getMessage())));
  }

  @ResponseStatus(NOT_FOUND)
  @ExceptionHandler({ResourceNotFoundException.class})
  public Errors handleResourceNotFoundException(final ResourceNotFoundException exception) {

    log.error("Resource not found: {}", exception.getMessage());

    return tendersAPIModelUtils.buildDefaultErrors(NOT_FOUND.toString(),
        Constants.ERR_MSG_RESOURCE_NOT_FOUND, exception.getMessage());
  }

  @ResponseStatus(INTERNAL_SERVER_ERROR)
  @ExceptionHandler(Exception.class)
  public Errors handleUnknownException(final Exception exception) {

    log.error("Unknown application exception", exception);

    return tendersAPIModelUtils.buildDefaultErrors(INTERNAL_SERVER_ERROR.toString(),
        Constants.ERR_MSG_DEFAULT, exception.getMessage());
  }

  @ResponseStatus(INTERNAL_SERVER_ERROR)
  @ExceptionHandler(UpstreamServiceException.class)
  public Errors handleUpstreamServiceException(final UpstreamServiceException exception) {

    log.error("Upstream application error", exception);

    return tendersAPIModelUtils.buildDefaultErrors(INTERNAL_SERVER_ERROR.toString(),
        Constants.ERR_MSG_UPSTREAM, exception.getMessage());
  }

  @ResponseStatus(BAD_REQUEST)
  @ExceptionHandler({ValidationException.class, HttpMessageNotReadableException.class,
      IllegalArgumentException.class, MethodArgumentNotValidException.class})
  public Errors handleValidationException(final Exception exception) {

    log.trace("Request validation exception", exception);

    return tendersAPIModelUtils.buildDefaultErrors(BAD_REQUEST.toString(),
        Constants.ERR_MSG_VALIDATION, exception.getMessage());
  }

  @ResponseStatus(INTERNAL_SERVER_ERROR)
  @ExceptionHandler({WebClientResponseException.class})
  public Errors handleWebClientResponseException(final WebClientResponseException exception) {

    final Optional<HttpRequest> request = Optional.ofNullable(exception.getRequest());

    var invokedService = "unknown";
    if (request.isPresent()) {
      invokedService = request.get().getMethod() + " " + request.get().getURI();
    }

    var logErrorMsg =
        String.format("Error invoking upstream service [%s], received status: [%d], body: [%s]",
            invokedService, exception.getRawStatusCode(), exception.getResponseBodyAsString());

    log.error(logErrorMsg, exception);

    return tendersAPIModelUtils.buildDefaultErrors(INTERNAL_SERVER_ERROR.toString(),
        Constants.ERR_MSG_UPSTREAM, exception.getMessage());
  }

  @ResponseStatus(I_AM_A_TEAPOT)
  @ExceptionHandler({LoginDirectorEdgeCaseException.class})
  public Errors handleLoginDirectorEdgeCaseException(
      final LoginDirectorEdgeCaseException exception) {
    log.warn(exception.getMessage());

    return tendersAPIModelUtils.buildDefaultErrors(I_AM_A_TEAPOT.toString(),
        "Login Director Edgecase Scenario", exception.getMessage());
  }


  @ResponseStatus(INTERNAL_SERVER_ERROR)
  @ExceptionHandler({JaggaerUserExistException.class})
  public Errors handleJaggaerUserExistException(
          final JaggaerUserExistException exception) {
    log.warn(exception.getMessage());

    return tendersAPIModelUtils.buildDefaultErrors(INTERNAL_SERVER_ERROR.toString(),
            "Jaggaer User Exist Scenario", exception.getMessage());

  }

  @ResponseStatus(BAD_REQUEST)
  @ExceptionHandler({JaggaerUserNotExistException.class})
  public Errors handleJaggaerUserNotExistException(
          final JaggaerUserNotExistException exception) {
    log.warn(exception.getMessage());

    return tendersAPIModelUtils.buildDefaultErrors(BAD_REQUEST.toString(),
            "Jaggaer User Not Exist Scenario", exception.getMessage());

  }
  @ResponseStatus(BAD_REQUEST)
  @ExceptionHandler({NotSupportedException.class})
  public Errors handleNotSupportedException(
      final NotSupportedException exception) {
    log.warn(exception.getMessage());

    return tendersAPIModelUtils.buildDefaultErrors(BAD_REQUEST.toString(),
        "Mime type not supported ", exception.getMessage());
  }

  @ResponseStatus(BAD_REQUEST)
  @ExceptionHandler({OperationNotSupportedException.class})
  public Errors handleOperationNotSupportedException(
          final OperationNotSupportedException exception) {
    log.warn(exception.getMessage());

    return tendersAPIModelUtils.buildDefaultErrors(BAD_REQUEST.toString(),
            "Can not complete an Event", exception.getMessage());
  }
  
  @ResponseStatus(INTERNAL_SERVER_ERROR)
  @ExceptionHandler({DuplicateFiscalCodeException.class})
  public Errors handleDuplicateFiscalCodeException(
          final DuplicateFiscalCodeException exception) {
    log.error(exception.getMessage());

    return tendersAPIModelUtils.buildDefaultErrors(INTERNAL_SERVER_ERROR.toString(),
            "Login Director Edgecase Scenario", exception.getMessage());

  }


}
