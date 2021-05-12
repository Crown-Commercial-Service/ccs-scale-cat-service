package uk.gov.crowncommercial.dts.scale.cat.config;

import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.resource.OAuth2ResourceServerConfigurer;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import lombok.extern.slf4j.Slf4j;

/**
 * OAuth2 / JWT web security configuration
 */
// @EnableGlobalMethodSecurity(prePostEnabled = true)
@EnableWebSecurity
@Slf4j
public class OAuth2Config extends WebSecurityConfigurerAdapter {

  @Override
  protected void configure(HttpSecurity http) throws Exception {

    log.info("Configuring resource server...");

    // @formatter:off
    http.authorizeRequests(authz -> {
      authz
        // .antMatchers(HttpMethod.POST,
        // "/tenders/projects/agreements").hasAnyAuthority("CAT_USER")
        .antMatchers(HttpMethod.POST, "/tenders/projects/agreements").authenticated()

        .anyRequest().denyAll();

    })
    // TODO: CSRF protection - Yes/No?
    // .csrf(CsrfConfigurer::disable)
    .oauth2ResourceServer(OAuth2ResourceServerConfigurer::jwt);
    // @formatter:on
  }

  // @Bean
  // public GrantedAuthorityDefaults grantedAuthorityDefaults() {
  // return new GrantedAuthorityDefaults("");
  // }

  @Bean
  public JwtAuthenticationConverter jwtAuthenticationConverter() {
    var grantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
    grantedAuthoritiesConverter.setAuthoritiesClaimName("roles");
    grantedAuthoritiesConverter.setAuthorityPrefix("");

    var jwtAuthenticationConverter = new JwtAuthenticationConverter();
    jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesConverter);
    return jwtAuthenticationConverter;
  }

}
