package uk.gov.crowncommercial.dts.scale.cat.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Native Jackson 2 configuration without deprecated Spring wrappers.
 */
@Configuration
@Slf4j
public class JacksonConfig {

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        log.debug("Configuring Jackson 2 ObjectMapper...");

        return JsonMapper.builder()
                // Core Java 8+ modules
                .addModule(new Jdk8Module())              // Handles java.util.Optional
                .addModule(new JavaTimeModule())          // Handles Instant, LocalDate, etc.
                .addModule(new ParameterNamesModule())    // Handles constructors without @JsonCreator
                .findAndAddModules()

                .defaultPropertyInclusion(JsonInclude.Value.construct(
                        JsonInclude.Include.NON_NULL,
                        JsonInclude.Include.NON_NULL
                ))

                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(
                        DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE,
                        DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
                )

                .propertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE)
                .visibility(PropertyAccessor.GETTER, Visibility.NONE)

                .build();
    }
}