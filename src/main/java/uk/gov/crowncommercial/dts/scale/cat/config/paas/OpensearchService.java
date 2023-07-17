package uk.gov.crowncommercial.dts.scale.cat.config.paas;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class OpensearchService {
    OpensearchCredentials credentials;

    /* Service name - useful for filtering */
    String name;
}
