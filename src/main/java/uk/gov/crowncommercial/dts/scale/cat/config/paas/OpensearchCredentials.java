package uk.gov.crowncommercial.dts.scale.cat.config.paas;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class OpensearchCredentials {

    @JsonProperty("hostname")
    String hostname;

    @JsonProperty("username")
    String username;

    @JsonProperty("password")
    String password;

    @JsonProperty("port")
    String port;
}
