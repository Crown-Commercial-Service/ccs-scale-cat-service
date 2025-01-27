package uk.gov.crowncommercial.dts.scale.cat.model.jaggaer;

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
public class SSOCodeData {

  @Value
  @Builder
  @Jacksonized
  public static class SSOCode {

    String ssoCodeValue;
    String ssoUserLogin;
  }

  @JsonProperty("ssoCode")
  Set<SSOCode> ssoCode;

}
