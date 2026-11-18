package uk.gov.crowncommercial.dts.scale.cat.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcJacksonConfig implements WebMvcConfigurer {

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        // 1. If a Jackson 2 converter already exists, update its ObjectMapper
        for (HttpMessageConverter<?> converter : converters) {
            if (converter instanceof MappingJackson2HttpMessageConverter jacksonConverter) {
                jacksonConverter.setObjectMapper(objectMapper);
                return;
            }
        }

        // 2. Otherwise, insert it AFTER StringHttpMessageConverter so Strings stay unquoted
        int stringConverterIndex = -1;
        for (int i = 0; i < converters.size(); i++) {
            if (converters.get(i) instanceof StringHttpMessageConverter) {
                stringConverterIndex = i;
                break;
            }
        }

        MappingJackson2HttpMessageConverter jsonConverter =
                new MappingJackson2HttpMessageConverter(objectMapper);

        if (stringConverterIndex != -1) {
            converters.add(stringConverterIndex + 1, jsonConverter);
        } else {
            converters.add(jsonConverter);
        }
    }
}