package uk.gov.crowncommercial.dts.scale.cat.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import lombok.RequiredArgsConstructor;

/**
 *
 */
@Configuration
@RequiredArgsConstructor
public class DocumentUploadS3ClientConfig {

  private final DocumentUploadAPIConfig documentUploadAPIConfig;

  @Bean("documentUploadS3Client")
  public AmazonS3 amazonS3() {
    if(documentUploadAPIConfig.getAwsAccessKeyId() != null) {
      var awsCredentials = new AWSStaticCredentialsProvider(new BasicAWSCredentials(
          documentUploadAPIConfig.getAwsAccessKeyId().toString(), documentUploadAPIConfig.getAwsSecretKey().toString()));

      return AmazonS3ClientBuilder.standard().withRegion(documentUploadAPIConfig.getAwsRegion())
          .withCredentials(awsCredentials).build();
    } else {
      return AmazonS3ClientBuilder.standard().withRegion(documentUploadAPIConfig.getAwsRegion())
          .build();
    }
  }

}
