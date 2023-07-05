package uk.gov.crowncommercial.dts.scale.cat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Data;

/**
 *
 */
@Configuration
@ConfigurationProperties(prefix = "config.external.s3", ignoreUnknownFields = true)
@Data
public class OppertunitiesS3Config {

  String awsRegion;
  String bucket;
  String accessKeyId;
  String secretAccessKey;
  String objectPrefix;

}
