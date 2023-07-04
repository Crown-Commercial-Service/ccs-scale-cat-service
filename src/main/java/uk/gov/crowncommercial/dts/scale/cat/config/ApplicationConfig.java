package uk.gov.crowncommercial.dts.scale.cat.config;

import java.time.Clock;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import com.fasterxml.jackson.databind.ObjectMapper;
import uk.gov.crowncommercial.dts.scale.cat.config.paas.VCAPServices;

/**
 *
 */
@Configuration
public class ApplicationConfig {

  @Autowired
  private Environment environment;

  @Autowired
  private ObjectMapper objectMapper;

  @Bean
  public Clock utcClock() {
    return Clock.systemUTC();
  }

  @Bean
  public VCAPServices vcapServices() throws Exception {
    var envVCAPServices = environment.getProperty("VCAP_SERVICES");
    return objectMapper.readValue(envVCAPServices, VCAPServices.class);
  }

}
