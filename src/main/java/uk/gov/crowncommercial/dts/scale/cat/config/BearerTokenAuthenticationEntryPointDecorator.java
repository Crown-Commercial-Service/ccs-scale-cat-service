package uk.gov.crowncommercial.dts.scale.cat.config;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.bind.annotation.ControllerAdvice;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.Errors;

/**
 *
 */
@ControllerAdvice
@RequiredArgsConstructor
public class BearerTokenAuthenticationEntryPointDecorator implements AuthenticationEntryPoint {

  private final ObjectMapper objectMapper;
  private final BearerTokenAuthenticationEntryPoint bearerTokenAuthenticationEntryPoint =
      new BearerTokenAuthenticationEntryPoint();

  @Override
  public void commence(HttpServletRequest request, HttpServletResponse response,
      AuthenticationException authException) throws IOException, ServletException {
    // 401
    bearerTokenAuthenticationEntryPoint.commence(request, response, authException);
    response.getWriter().write(objectMapper.writeValueAsString(new Errors()));
  }

}
