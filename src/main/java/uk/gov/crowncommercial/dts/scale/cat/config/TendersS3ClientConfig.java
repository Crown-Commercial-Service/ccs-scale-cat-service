package uk.gov.crowncommercial.dts.scale.cat.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import lombok.RequiredArgsConstructor;
import uk.gov.crowncommercial.dts.scale.cat.config.paas.AWSS3Service;
import uk.gov.crowncommercial.dts.scale.cat.config.paas.VCAPServices;

/**
 *
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
  public AmazonS3 amazonS3(final AWSS3Service awsS3Bucket) {
    var bucketCredentials = awsS3Bucket.getCredentials();
    if(bucketCredentials.getAwsAccessKeyId() != null) {
      var awsCredentials = new AWSStaticCredentialsProvider(new BasicAWSCredentials(
          bucketCredentials.getAwsAccessKeyId().toString(), bucketCredentials.getAwsSecretAccessKey().toString()));

      return AmazonS3ClientBuilder.standard().withRegion(bucketCredentials.getAwsRegion())
          .withCredentials(awsCredentials).build();
    } else {
      return AmazonS3ClientBuilder.standard().withRegion(bucketCredentials.getAwsRegion())
          .build();
    }
  }

}
