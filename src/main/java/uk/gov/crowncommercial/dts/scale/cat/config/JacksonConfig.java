package uk.gov.crowncommercial.dts.scale.cat.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.boot.jackson2.autoconfigure.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

/**
 * Jackson configuration
 */
@Configuration
@Slf4j
public class JacksonConfig {

  @Bean
  public Jackson2ObjectMapperBuilderCustomizer configureJackson() {
    log.debug("Configuring Jackson behaviour...");

    return jacksonObjectMapperBuilder -> {
      jacksonObjectMapperBuilder.serializationInclusion(JsonInclude.Include.NON_NULL);
      jacksonObjectMapperBuilder.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS,
              DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE,
              DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
      jacksonObjectMapperBuilder.propertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE);
      jacksonObjectMapperBuilder.visibility(PropertyAccessor.GETTER, Visibility.NONE);

    };
  }
}