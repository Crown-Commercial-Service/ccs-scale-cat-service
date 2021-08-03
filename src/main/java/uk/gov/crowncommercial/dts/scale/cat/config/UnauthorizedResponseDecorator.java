package uk.gov.crowncommercial.dts.scale.cat.config;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import java.io.IOException;
import java.util.Arrays;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
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
public class UnauthorizedResponseDecorator implements AuthenticationEntryPoint {

  private final ObjectMapper objectMapper;
  private final TendersAPIModelUtils tendersAPIModelUtils;
  private final BearerTokenAuthenticationEntryPoint bearerTokenAuthenticationEntryPoint =
      new BearerTokenAuthenticationEntryPoint();

  @Override
  public void commence(HttpServletRequest request, HttpServletResponse response,
      AuthenticationException authException) throws IOException, ServletException {

    bearerTokenAuthenticationEntryPoint.commence(request, response, authException);
    var error401 = new ApiError(UNAUTHORIZED.toString(), Constants.ERR_MSG_INVALID_JWT, "");
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.getWriter().write(
        objectMapper.writeValueAsString(tendersAPIModelUtils.buildErrors(Arrays.asList(error401))));
  }

}
