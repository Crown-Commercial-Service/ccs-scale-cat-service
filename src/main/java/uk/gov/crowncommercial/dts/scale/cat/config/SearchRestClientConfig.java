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
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;

@Configuration
@ConfigurationProperties(prefix = "config.external.opensearch", ignoreUnknownFields = true)
@Data
public class SearchRestClientConfig extends AbstractOpenSearchConfiguration {
    private String hostname;
    private String port;
    private String userName;
    private String password;
    @SneakyThrows
    @Override
    @Bean
    public RestHighLevelClient opensearchClient() {

         SSLContext sslContext = SSLContexts.custom()
                .loadTrustMaterial(null, TrustAllStrategy.INSTANCE)
                .build();
        String hostnameurl=getHostname()+":"+getPort();
        final ClientConfiguration clientConfiguration = ClientConfiguration.builder()
                .connectedTo(hostnameurl)
                .usingSsl(sslContext, NoopHostnameVerifier.INSTANCE)
                .withBasicAuth(getUserName(),getPassword())
                .build();

        return RestClients.create(clientConfiguration).rest();
    }
}
