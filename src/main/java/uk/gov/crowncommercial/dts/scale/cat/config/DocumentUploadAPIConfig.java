package uk.gov.crowncommercial.dts.scale.cat.config;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Data;

/**
 *
 */
@Configuration
@ConfigurationProperties(prefix = "config.external.document-upload", ignoreUnknownFields = true)
@Data
public class DocumentUploadAPIConfig {

  public static final String KEY_URI_TEMPLATE = "uriTemplate";

  private String baseUrl;
  private String apiKey;

  // AWS-S3
  private String awsRegion;
  private String awsAccessKeyId;
  private String awsSecretKey;
  private String s3Bucket;

  private Integer timeoutDuration;
  private String documentStateProcessing;
  private String documentStateSafe;
  private String documentStateUnsafe;
  private Map<String, String> postDocument;
  private Map<String, String> getDocumentRecord;

}
