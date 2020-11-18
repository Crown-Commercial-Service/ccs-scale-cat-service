package uk.gov.crowncommercial.dts.scale.cat.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import com.rollbar.notifier.Rollbar;
import com.rollbar.notifier.config.Config;
import com.rollbar.spring.webmvc.RollbarSpringConfigBuilder;

/**
 * Taken from https://docs.rollbar.com/docs/spring
 *
 */
@Configuration
@ComponentScan("uk.gov.crowncommercial.dts.scale.cat")
public class RollbarConfig {

  /**
   * Register a Rollbar bean to configure App with Rollbar. Provide Rollbar access token via
   * environment variable <code>rollbar.access-token</code>
   */
  @Bean
  public Rollbar rollbar(
      @Value("${rollbar.access-token:override-via-env}") final String rollbarAccessToken) {
    return new Rollbar(getRollbarConfigs(rollbarAccessToken));
  }

  private Config getRollbarConfigs(final String accessToken) {

    // Reference ConfigBuilder.java for all the properties you can set for Rollbar
    return RollbarSpringConfigBuilder.withAccessToken(accessToken).environment("development")
        .build();
  }

}
