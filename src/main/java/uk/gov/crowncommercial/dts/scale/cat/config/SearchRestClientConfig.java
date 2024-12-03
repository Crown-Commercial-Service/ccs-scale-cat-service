package uk.gov.crowncommercial.dts.scale.cat.config;

import lombok.Data;
import lombok.SneakyThrows;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.TrustAllStrategy;
import org.apache.http.ssl.SSLContexts;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.data.client.orhlc.AbstractOpenSearchConfiguration;
import org.opensearch.data.client.orhlc.ClientConfiguration;
import org.opensearch.data.client.orhlc.RestClients;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.net.ssl.SSLContext;

@Configuration
@Data
public class SearchRestClientConfig extends AbstractOpenSearchConfiguration {

    private final OpensearchCredentials opensearchCredentials;

    @Autowired
    public SearchRestClientConfig(OpensearchCredentials opensearchCredentials) {
        this.opensearchCredentials = opensearchCredentials;
    }

    @SneakyThrows
    @Override
    @Bean
    public RestHighLevelClient opensearchClient() {
        SSLContext sslContext = SSLContexts.custom()
                .loadTrustMaterial(null, TrustAllStrategy.INSTANCE)
                .build();

        String hostnameurl = opensearchCredentials.getHostname() + ":" + opensearchCredentials.getPort();

        ClientConfiguration.MaybeSecureClientConfigurationBuilder clientConfigurationBuilder =
                ClientConfiguration.builder().connectedTo(hostnameurl);

        // Only add basic auth if configured
        opensearchCredentials.getUsername().ifPresent(username ->
                opensearchCredentials.getPassword().ifPresent(password ->
                        clientConfigurationBuilder.withBasicAuth(username, password)));

        // Enforce use of SSL
        clientConfigurationBuilder.usingSsl(sslContext, NoopHostnameVerifier.INSTANCE);

        ClientConfiguration clientConfiguration = clientConfigurationBuilder.build();
        return RestClients.create(clientConfiguration).rest();
    }
}