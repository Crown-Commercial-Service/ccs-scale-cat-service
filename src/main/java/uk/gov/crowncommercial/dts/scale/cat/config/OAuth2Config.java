package uk.gov.crowncommercial.dts.scale.cat.config;

import java.util.Arrays;
import java.util.stream.Stream;

import org.apache.commons.lang3.ArrayUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationFilter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.auth.Authorities;
import uk.gov.crowncommercial.dts.scale.cat.auth.apikey.ApiKeyAuthFilter;
import uk.gov.crowncommercial.dts.scale.cat.auth.apikey.ApiKeyAuthManager;
import uk.gov.crowncommercial.dts.scale.cat.auth.apikey.ApiKeyDetailsProvider;
import uk.gov.crowncommercial.dts.scale.cat.auth.apikey.ConfigPropertiesApiKeyDetailsProvider;

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
      new String[] { Authorities.CAT_USER_ROLE, Authorities.CAT_ADMINISTRATOR_ROLE, Authorities.ACCESS_CAT_ROLE };

  private static final String[] LD_ROLES = new String[] { Authorities.JAEGGER_BUYER_ROLE, Authorities.JAEGGER_SUPPLIER_ROLE };

  private static final String[] LD_AND_CAT_ROLES = ArrayUtils.addAll(CAT_ROLES, LD_ROLES);

  @Override
  protected void configure(final HttpSecurity http) throws Exception {

    log.info("Configuring resource server...");

	// @formatter:off
    http.addFilterBefore(apiKeyFilter(), BearerTokenAuthenticationFilter.class)
    	.authorizeRequests(authz ->
      authz
        // TODO (pillingworth, 2023-03-01) better to apply at method/class level using RolesAllowed annotation than applying globally with wildcards?
        .antMatchers("/tenders/projects/salesforce").hasAuthority(Authorities.ESOURCING_ROLE)
        .antMatchers("/tenders/projects/deltas").hasAuthority(Authorities.ESOURCING_ROLE)
        .antMatchers("/tenders/projects/*/events/*/termination").hasAnyAuthority(
        		ArrayUtils.addAll(CAT_ROLES, Authorities.ESOURCING_ROLE))
        .antMatchers("/tenders/projects/**").hasAnyAuthority(CAT_ROLES)
        .antMatchers("/tenders/event-types").hasAnyAuthority(CAT_ROLES)
        .antMatchers("/journeys/**").hasAnyAuthority(CAT_ROLES)
        .antMatchers("/assessments/**").hasAnyAuthority(CAT_ROLES)
        .antMatchers("/tenders/users/**").hasAnyAuthority(LD_AND_CAT_ROLES)
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
    log.info("Configuring custom JwtAuthenticationConverter to read CAT roles.");

    var grantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
    grantedAuthoritiesConverter.setAuthoritiesClaimName("roles");
    grantedAuthoritiesConverter.setAuthorityPrefix("");

    var jwtAuthenticationConverter = new JwtAuthenticationConverter();
    jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesConverter);

    return jwtAuthenticationConverter;
  }

  @Bean
  ApiKeyAuthFilter apiKeyFilter() {
    ApiKeyAuthFilter filter = new ApiKeyAuthFilter(apiKeyConfig().getHeader());
    filter.setAuthenticationManager(new ApiKeyAuthManager(apiKeyDetailsProvider()));
      return filter;
	}
	
    @Bean
    ApiKeyDetailsProvider apiKeyDetailsProvider() {
      return new ConfigPropertiesApiKeyDetailsProvider(apiKeyConfig());
    }

    @Bean
    ApiKeyConfig apiKeyConfig() {
      return new ApiKeyConfig();
    }
}
