package uk.gov.crowncommercial.dts.scale.cat.config;

import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
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
    http.authorizeRequests(authz ->
      authz
        .antMatchers(HttpMethod.POST, "/tenders/**").hasAnyAuthority("CAT_USER", "CAT_ADMINISTRATOR")
        .anyRequest().denyAll()
    )
    .csrf(CsrfConfigurer::disable)
    .oauth2ResourceServer().jwt().jwtAuthenticationConverter(jwtAuthenticationConverter());
    // @formatter:on
  }

  @Bean
  public JwtAuthenticationConverter jwtAuthenticationConverter() {
    log.info("Configuring custom JwtAuthenticationConverter to read CAT roles..");

    var grantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
    grantedAuthoritiesConverter.setAuthoritiesClaimName("roles");
    grantedAuthoritiesConverter.setAuthorityPrefix("");

    var jwtAuthenticationConverter = new JwtAuthenticationConverter();
    jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesConverter);
    jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesConverter);

    return jwtAuthenticationConverter;
  }

}
