package uk.gov.crowncommercial.dts.scale.cat.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.resource.OAuth2ResourceServerConfigurer;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

/**
 * OAuth2 / JWT web security configuration
 */
@Configuration
// @EnableGlobalMethodSecurity(prePostEnabled = true)
@EnableWebSecurity
public class OAuth2Config extends WebSecurityConfigurerAdapter {

  @Override
  protected void configure(HttpSecurity http) throws Exception {

    // @formatter:off
    http.authorizeRequests(authz -> {
      authz
        // .antMatchers(HttpMethod.POST,
        // "/tenders/projects/agreements").hasAnyAuthority("ORG_ADMINISTRATOR")
        .antMatchers(HttpMethod.POST, "/tenders/projects/agreements").authenticated()
        .anyRequest().denyAll();

    }).oauth2ResourceServer(OAuth2ResourceServerConfigurer::jwt);
    // @formatter:on
  }

  // @Bean
  // public GrantedAuthorityDefaults grantedAuthorityDefaults() {
  // return new GrantedAuthorityDefaults("");
  // }

  @Bean
  public JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtGrantedAuthoritiesConverter grantedAuthoritiesConverter =
        new JwtGrantedAuthoritiesConverter();
    grantedAuthoritiesConverter.setAuthoritiesClaimName("roles");
    grantedAuthoritiesConverter.setAuthorityPrefix("");

    JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
    jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesConverter);
    return jwtAuthenticationConverter;
  }

}
