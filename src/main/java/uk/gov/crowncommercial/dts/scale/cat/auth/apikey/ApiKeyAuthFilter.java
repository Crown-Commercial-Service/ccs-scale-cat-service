package uk.gov.crowncommercial.dts.scale.cat.auth.apikey;

import javax.servlet.http.HttpServletRequest;
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter;

/**
 * Spring security authentication filter for HTTP header based API Key authentication. 
 */
public class ApiKeyAuthFilter extends AbstractPreAuthenticatedProcessingFilter {

  private final String headerName;

  public ApiKeyAuthFilter(final String headerName) {
    if (headerName == null || headerName.isEmpty()) {
      throw new IllegalArgumentException("headerName");
    }
    this.headerName = headerName;
  }

  @Override
  protected Object getPreAuthenticatedPrincipal(HttpServletRequest request) {
    return request.getHeader(headerName);
  }

  @Override
  protected Object getPreAuthenticatedCredentials(HttpServletRequest request) {
    // There are no credentials when using API key
    return null;
  }
}
