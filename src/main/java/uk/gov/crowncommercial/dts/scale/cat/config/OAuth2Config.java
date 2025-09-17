package uk.gov.crowncommercial.dts.scale.cat.config;

import java.util.Arrays;
import java.util.stream.Stream;

import org.apache.commons.lang3.ArrayUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.web.SecurityFilterChain;

/**
 * OAuth2 / JWT web security configuration
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
@Slf4j
public class OAuth2Config {

  private final UnauthorizedResponseDecorator unauthorizedResponseDecorator;
  private final AccessDeniedResponseDecorator accessDeniedResponseDecorator;
  private static final String[] CAT_ROLES =
      new String[] {"CAT_USER", "CAT_ADMINISTRATOR", "ACCESS_CAT"};

  private static final String[] LD_ROLES = new String[] {"JAEGGER_BUYER", "JAEGGER_SUPPLIER"};

  private static final String[] LD_AND_CAT_ROLES = ArrayUtils.addAll(CAT_ROLES, LD_ROLES);

  @Bean
  protected SecurityFilterChain configure(final HttpSecurity http) throws Exception {

    log.info("Configuring resource server...");

    http
      .authorizeHttpRequests(authz -> authz
        .requestMatchers("/actuator/**").permitAll()
        .requestMatchers(HttpMethod.GET,"/tenders/projects/*").permitAll()
        .requestMatchers(HttpMethod.GET,"/tenders/projects/*/events/*/documents/export").permitAll()
        .requestMatchers("/tenders/projects/**").hasAnyAuthority(CAT_ROLES)
        .requestMatchers("/tenders/supplier/**").permitAll()
        .requestMatchers("/audit/**").permitAll()
        .requestMatchers("/tenders/event-types").hasAnyAuthority(CAT_ROLES)
        .requestMatchers("/journeys/**").hasAnyAuthority(CAT_ROLES)
        .requestMatchers("/assessments/**").hasAnyAuthority(CAT_ROLES)
        .requestMatchers("/suppliers/**").hasAnyAuthority(CAT_ROLES)
        .requestMatchers("/tenders/users/**").hasAnyAuthority(LD_AND_CAT_ROLES)
        .requestMatchers("/tenders/orgs/**").hasAnyAuthority(LD_AND_CAT_ROLES)
        .requestMatchers("/error/**").hasAnyAuthority(
            Stream.concat(Arrays.stream(CAT_ROLES), Arrays.stream(LD_ROLES)).toArray(String[]::new))
        .anyRequest().denyAll()
      )
      .csrf(CsrfConfigurer::disable)
      .oauth2ResourceServer(oauth2 -> oauth2
        .authenticationEntryPoint(unauthorizedResponseDecorator)
        .accessDeniedHandler(accessDeniedResponseDecorator)
        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
      );

    return http.build();
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
