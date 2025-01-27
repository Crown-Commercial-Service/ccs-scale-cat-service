package uk.gov.crowncommercial.dts.scale.cat.config.paas;

import java.util.Set;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 *
 */
@Value
@Builder
@Jacksonized
public class VCAPServices {

  @JsonProperty("aws-s3-bucket")
  Set<AWSS3Service> awsS3Services;

  @JsonProperty("opensearch")
  Set<OpensearchService> opensearch;

}
