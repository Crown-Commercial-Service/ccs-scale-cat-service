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
public class RPATransferS3ClientConfig {

  private final RPATransferS3Config rpaTransferS3Config;

  @Bean("rpaTransferS3Client")
  public AmazonS3 amazonS3() {
    var awsCredentials = new AWSStaticCredentialsProvider(new BasicAWSCredentials(
        rpaTransferS3Config.getAccessKeyId(), rpaTransferS3Config.getSecretAccessKey()));

    return AmazonS3ClientBuilder.standard().withRegion(rpaTransferS3Config.getRegion())
        .withCredentials(awsCredentials).build();
  }

}
