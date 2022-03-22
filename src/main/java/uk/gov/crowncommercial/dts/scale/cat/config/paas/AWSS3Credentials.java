package uk.gov.crowncommercial.dts.scale.cat.config.paas;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class AWSS3Credentials {

  @JsonProperty("aws_region")
  String awsRegion;

  @JsonProperty("bucket_name")
  String bucketName;

  @JsonProperty("aws_access_key_id")
  String awsAccessKeyId;

  @JsonProperty("aws_secret_access_key")
  String awsSecretAccessKey;

}
