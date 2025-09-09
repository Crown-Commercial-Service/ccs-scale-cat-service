package uk.gov.crowncommercial.dts.scale.cat.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import lombok.RequiredArgsConstructor;

/**
 * Web Client for accessing Doc Upload S3 Bucket
 */
@Configuration
@RequiredArgsConstructor
public class DocumentUploadS3ClientConfig {

  private final DocumentUploadAPIConfig documentUploadAPIConfig;

  @Bean("documentUploadS3Client")
  public S3Client s3Client() {
    if(documentUploadAPIConfig.getAwsAccessKeyId() != null && documentUploadAPIConfig.getAwsAccessKeyId().isPresent() && documentUploadAPIConfig.getAwsSecretKey() != null && documentUploadAPIConfig.getAwsSecretKey().isPresent()) {
      AwsBasicCredentials awsCredentials = AwsBasicCredentials.create(
          documentUploadAPIConfig.getAwsAccessKeyId().get(), documentUploadAPIConfig.getAwsSecretKey().get());

      return S3Client.builder()
          .region(Region.of(documentUploadAPIConfig.getAwsRegion()))
          .credentialsProvider(StaticCredentialsProvider.create(awsCredentials))
          .build();
    } else {
      return S3Client.builder()
          .region(Region.of(documentUploadAPIConfig.getAwsRegion()))
          .build();
    }
  }

}
