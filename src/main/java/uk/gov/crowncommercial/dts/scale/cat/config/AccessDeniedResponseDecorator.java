package uk.gov.crowncommercial.dts.scale.cat.config;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import java.io.IOException;
import java.util.Arrays;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.bind.annotation.ControllerAdvice;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import uk.gov.crowncommercial.dts.scale.cat.model.ApiError;
import uk.gov.crowncommercial.dts.scale.cat.utils.TendersAPIModelUtils;

/**
 * Delegates to {@link BearerTokenAccessDeniedHandler} to set correct headers and status code, etc -
 * then adds the standard Tenders API error response body
 */
@ControllerAdvice
@RequiredArgsConstructor
public class AccessDeniedResponseDecorator implements AccessDeniedHandler {

  private final ObjectMapper objectMapper;
  private final TendersAPIModelUtils tendersAPIModelUtils;
  private final BearerTokenAccessDeniedHandler bearerTokenAccessDeniedHandler =
      new BearerTokenAccessDeniedHandler();

  @Override
  public void handle(HttpServletRequest request, HttpServletResponse response,
      AccessDeniedException accessDeniedException) throws IOException, ServletException {

    bearerTokenAccessDeniedHandler.handle(request, response, accessDeniedException);
    var error403 = new ApiError(FORBIDDEN.toString(), Constants.ERR_MSG_FORBIDDEN, "");
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.getWriter().write(
        objectMapper.writeValueAsString(tendersAPIModelUtils.buildErrors(Arrays.asList(error403))));

  }

}
