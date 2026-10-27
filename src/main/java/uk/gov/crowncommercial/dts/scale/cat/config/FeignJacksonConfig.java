package uk.gov.crowncommercial.dts.scale.cat.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.codec.Decoder;
import feign.jackson.JacksonDecoder;
import feign.optionals.OptionalDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Tells Feign to use our custom ObjectMapper rather than its own
 */
@Configuration
public class FeignJacksonConfig {
    @Bean
    public Decoder feignDecoder(ObjectMapper objectMapper) {
        // Uses your Jackson 2 ObjectMapper directly
        return new OptionalDecoder(new JacksonDecoder(objectMapper));
    }
}