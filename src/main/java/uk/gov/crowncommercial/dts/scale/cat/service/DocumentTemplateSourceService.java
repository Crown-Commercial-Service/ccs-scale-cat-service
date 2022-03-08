package uk.gov.crowncommercial.dts.scale.cat.service;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import lombok.Data;

/**
 * Document template source service. In the future, the proforma templates should be sourced from a
 * central location.
 */
@Configuration
@ConfigurationProperties("config.document.templates")
@Data
public class DocumentTemplateSourceService {

  private Map<String, Set<String>> eventTypeProformas;

  public Set<Resource> getEventTypeTemplates(final String eventType) {
    return eventTypeProformas.getOrDefault(eventType, Collections.emptySet()).stream()
        .map(ClassPathResource::new).collect(Collectors.toSet());
  }

}
