package uk.gov.crowncommercial.dts.scale.cat.config;

import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.resource.OAuth2ResourceServerConfigurer;

/**
 * OAuth2 / JWT web security configuration
 */
public class OAuth2SecurityConfig extends WebSecurityConfigurerAdapter {

  @Override
  protected void configure(HttpSecurity http) throws Exception {

    // @formatter:off
    http.authorizeRequests(authz -> authz
        .antMatchers(HttpMethod.GET, "/tenders/**").hasAuthority("SCOPE_buyer")
        .antMatchers(HttpMethod.POST, "/tenders/projects/agreements").hasAuthority("SCOPE_buyer")
        .anyRequest().authenticated())
        .oauth2ResourceServer(OAuth2ResourceServerConfigurer::jwt);

    // @formatter:on
  }
}
