package uk.gov.crowncommercial.dts.scale.cat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Data
@Component
@ConfigurationProperties(prefix = "config.external.openSearch")
public class OpensearchCredentials {
    private String hostname;

    private String port;

    private Optional<String> username;

    private Optional<String> password;
}
