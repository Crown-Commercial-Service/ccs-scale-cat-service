package uk.gov.crowncommercial.dts.scale.cat.config.paas;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import java.util.Optional;

@Value
@Builder
@Jacksonized
public class OpensearchCredentials {

    @JsonProperty("hostname")
    String hostname;

    @JsonProperty("username")
    Optional<String> username;

    @JsonProperty("password")
    Optional<String> password;

    @JsonProperty("port")
    String port;
}
