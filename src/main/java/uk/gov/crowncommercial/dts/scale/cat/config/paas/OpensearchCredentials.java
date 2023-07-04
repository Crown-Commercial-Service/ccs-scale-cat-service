package uk.gov.crowncommercial.dts.scale.cat.config.paas;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class OpensearchCredentials {

    @JsonProperty("opensearch_hostname")
    String hostname;

    @JsonProperty("opensearch_username")
    String username;

    @JsonProperty("opensearch_password")
    String password;

    @JsonProperty("opensearch_port")
    String port;
}
