package uk.gov.crowncommercial.dts.scale.cat.auth.apikey;

import java.util.Collection;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.Transient;

/**
 * Spring security authentication token for HTTP header based API Key authentication.
 * 
 * The API Key authentication is not session based, hence makred as Transient.
 */
@Transient
public class ApiKeyAuthToken extends AbstractAuthenticationToken {

  private static final long serialVersionUID = 1L;

  private String apiKey;

  public ApiKeyAuthToken(String apiKey, Collection<? extends GrantedAuthority> authorities) {
    super(authorities);
    this.apiKey = apiKey;
    setAuthenticated(true);
  }

  @Override
  public Object getCredentials() {
    return null;
  }

  @Override
  public Object getPrincipal() {
    return apiKey;
  }
}
