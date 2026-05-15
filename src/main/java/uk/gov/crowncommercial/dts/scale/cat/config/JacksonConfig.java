package uk.gov.crowncommercial.dts.scale.cat.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import tools.jackson.databind.DeserializationFeature;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.PropertyNamingStrategies;

/**
 * Jackson configuration
 */
@Configuration
@Slf4j
public class JacksonConfig {

  @Bean
  public JsonMapperBuilderCustomizer configureJackson() {
    log.debug("Configuring Jackson behaviour...");

    return jacksonObjectMapperBuilder -> {
      jacksonObjectMapperBuilder.changeDefaultPropertyInclusion(value -> value.withValueInclusion(JsonInclude.Include.NON_NULL))
              .withConfigOverride(java.time.temporal.Temporal.class,cfg -> cfg.setFormat(com.fasterxml.jackson.annotation.JsonFormat.Value.forShape(com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING)))
              .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
              .propertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE)
              .changeDefaultVisibility(vc -> vc.withVisibility(PropertyAccessor.GETTER, Visibility.NONE));
    };
  }
}