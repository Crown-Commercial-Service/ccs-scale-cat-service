package uk.gov.crowncommercial.dts.scale.cat.config;

import lombok.Data;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.data.client.orhlc.AbstractOpenSearchConfiguration;
import org.opensearch.data.client.orhlc.ClientConfiguration;
import org.opensearch.data.client.orhlc.RestClients;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "config.external.opensearch", ignoreUnknownFields = true)
@Data
public class SearchRestClientConfig extends AbstractOpenSearchConfiguration {
    private String hostname;
    private String port;
    private String userName;
    private String password;
    @Override
    @Bean
    public RestHighLevelClient opensearchClient() {
        final ClientConfiguration clientConfiguration = ClientConfiguration.builder()
                .connectedTo(getHostname()+":"+getPort())
                .withBasicAuth(getUserName(),getPassword())
                .build();

        return RestClients.create(clientConfiguration).rest();
    }
}
