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
public class OppertunitiesS3ClientConfig {

  private final OppertunitiesS3Config oppertunitiesS3Config;

  @Bean("oppertunitiesS3Client")
  AmazonS3 amazonS3() {
    var awsCredentials = new AWSStaticCredentialsProvider(new BasicAWSCredentials(
        oppertunitiesS3Config.getAccessKeyId(), oppertunitiesS3Config.getSecretAccessKey()));
    return AmazonS3ClientBuilder.standard().withCredentials(awsCredentials)
        .withRegion(oppertunitiesS3Config.getAwsRegion()).build();
  }

}
