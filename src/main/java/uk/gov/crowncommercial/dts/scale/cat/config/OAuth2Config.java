package uk.gov.crowncommercial.dts.scale.cat.config;

import java.util.Arrays;
import java.util.stream.Stream;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * OAuth2 / JWT web security configuration
 */
// @EnableGlobalMethodSecurity(prePostEnabled = true)
@EnableWebSecurity
@RequiredArgsConstructor
@Slf4j
public class OAuth2Config extends WebSecurityConfigurerAdapter {

  private final UnauthorizedResponseDecorator unauthorizedResponseDecorator;
  private final AccessDeniedResponseDecorator accessDeniedResponseDecorator;

  // TODO: Verify and move to SSM
  private static final String[] CAT_ROLES =
      new String[] {"CAT_USER", "CAT_ADMINISTRATOR", "ACCESS_CAT", "CAT_USER_LOGIN_DIRECTOR"};

  private static final String[] LD_ROLES = new String[] {"JAEGGER_BUYER", "JAEGGER_SUPPLIER"};

  @Override
  protected void configure(final HttpSecurity http) throws Exception {

    log.info("Configuring resource server...");

    // @formatter:off
    http.authorizeRequests(authz ->
      authz
        .antMatchers("/tenders/projects/**").hasAnyAuthority(CAT_ROLES)
        .antMatchers("/tenders/event-types").hasAnyAuthority(CAT_ROLES)
        .antMatchers("/journeys/**").hasAnyAuthority(CAT_ROLES)
        .antMatchers("/tenders/users/**").hasAnyAuthority(LD_ROLES)
        .antMatchers("/tenders/orgs/**").hasAnyAuthority(LD_ROLES)
        .antMatchers("/error/**").hasAnyAuthority(
            Stream.concat(Arrays.stream(CAT_ROLES), Arrays.stream(LD_ROLES)).toArray(String[]::new))
        .anyRequest().denyAll()
    )
    .csrf(CsrfConfigurer::disable)
    .oauth2ResourceServer()
      .authenticationEntryPoint(unauthorizedResponseDecorator)
      .accessDeniedHandler(accessDeniedResponseDecorator)
      .jwt().jwtAuthenticationConverter(jwtAuthenticationConverter());

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
