package uk.gov.crowncommercial.dts.scale.cat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Data;

/**
 *
 */
@Configuration
@ConfigurationProperties(prefix = "config.external.s3.rpa", ignoreUnknownFields = true)
@Data
public class RPATransferS3Config {

  String region;
  String bucket;
  String accessKeyId;
  String secretAccessKey;
  String objectPrefix;
  String workbookPassword;

}
