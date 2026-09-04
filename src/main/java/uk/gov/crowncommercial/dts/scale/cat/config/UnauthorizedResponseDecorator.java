package uk.gov.crowncommercial.dts.scale.cat.config;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import java.io.IOException;
import java.util.Arrays;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.bind.annotation.ControllerAdvice;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import uk.gov.crowncommercial.dts.scale.cat.model.ApiError;
import uk.gov.crowncommercial.dts.scale.cat.utils.TendersAPIModelUtils;

/**
 * Delegates to {@link BearerTokenAuthenticationEntryPoint} to set correct headers and status code,
 * etc - then adds the standard Tenders API error response body
 */
@ControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class UnauthorizedResponseDecorator implements AuthenticationEntryPoint {

  private final ObjectMapper objectMapper;
  private final TendersAPIModelUtils tendersAPIModelUtils;
  private final BearerTokenAuthenticationEntryPoint bearerTokenAuthenticationEntryPoint =
      new BearerTokenAuthenticationEntryPoint();

  @Override
  public void commence(HttpServletRequest request, HttpServletResponse response,
      AuthenticationException authException) throws IOException, ServletException {

    log.warn(
        "Unauthorized request before controller invocation. method={}, uri={}, contentType={}, contentLength={}, authorizationHeader={}, error={}",
        request.getMethod(), request.getRequestURI(), request.getContentType(), request.getContentLengthLong(),
        describeAuthorizationHeader(request.getHeader(HttpHeaders.AUTHORIZATION)), authException.getMessage());

    bearerTokenAuthenticationEntryPoint.commence(request, response, authException);
    var error401 = new ApiError(UNAUTHORIZED.toString(), Constants.ERR_MSG_UNAUTHORISED, "");
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.getWriter().write(
        objectMapper.writeValueAsString(tendersAPIModelUtils.buildErrors(Arrays.asList(error401))));
  }

  private String describeAuthorizationHeader(final String authorizationHeader) {
    if (authorizationHeader == null || authorizationHeader.isBlank()) {
      return "missing";
    }

    return "present(length=" + authorizationHeader.length() + ")";
  }

}
