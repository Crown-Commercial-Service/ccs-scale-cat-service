package uk.gov.crowncommercial.dts.scale.cat.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import lombok.RequiredArgsConstructor;
import uk.gov.crowncommercial.dts.scale.cat.config.paas.AWSS3Service;
import uk.gov.crowncommercial.dts.scale.cat.config.paas.VCAPServices;

/**
 * Web Client for connecting to Tenders S3 bucket
 */
@Configuration
@RequiredArgsConstructor
public class TendersS3ClientConfig {

  static final String S3_BUCKET_SERVICE_PATTERN = "[a-z0-9]+-ccs-scale-cat-tenders-s3-documents";
  private final VCAPServices vcapServices;

  @Bean
  public AWSS3Service tendersS3Bucket() {

    return vcapServices.getAwsS3Services().stream()
        .filter(b -> b.getName().matches(S3_BUCKET_SERVICE_PATTERN)).findFirst().orElseThrow();
  }

  @Bean("tendersS3Client")
  public S3Client s3Client(final AWSS3Service awsS3Bucket) {
    var bucketCredentials = awsS3Bucket.getCredentials();
    if(bucketCredentials.getAwsAccessKeyId() != null && bucketCredentials.getAwsAccessKeyId().isPresent() && bucketCredentials.getAwsSecretAccessKey() != null && bucketCredentials.getAwsSecretAccessKey().isPresent()) {
      AwsBasicCredentials awsCredentials = AwsBasicCredentials.create(
          bucketCredentials.getAwsAccessKeyId().get(), bucketCredentials.getAwsSecretAccessKey().get());

      return S3Client.builder()
          .region(Region.of(bucketCredentials.getAwsRegion()))
          .credentialsProvider(StaticCredentialsProvider.create(awsCredentials))
          .build();
    } else {
      return S3Client.builder()
          .region(Region.of(bucketCredentials.getAwsRegion()))
          .build();
    }
  }

}
