package uk.gov.crowncommercial.dts.scale.cat.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Data;

/**
 *
 */
@Configuration
@ConfigurationProperties(prefix = "config.document", ignoreUnknownFields = true)
@Data
public class DocumentConfig {

  private List<String> allowedExtentions;
  private Integer maxSize;
  private Long maxTotalSize;
}
