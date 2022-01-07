package uk.gov.crowncommercial.dts.scale.cat.config;

import java.time.Clock;
import org.modelmapper.ModelMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 *
 */
@Configuration
public class ApplicationConfig {

  @Bean
  public Clock utcClock() {
    return Clock.systemUTC();
  }

  @Bean
  public ModelMapper modelMapper() {
    return new ModelMapper();
  }

}
