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
import uk.gov.crowncommercial.dts.scale.cat.config.paas.AWSS3Service;
import uk.gov.crowncommercial.dts.scale.cat.config.paas.OpensearchCredentials;
import uk.gov.crowncommercial.dts.scale.cat.config.paas.OpensearchService;
import uk.gov.crowncommercial.dts.scale.cat.config.paas.VCAPServices;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;

@Configuration

@Data
public class SearchRestClientConfig extends AbstractOpenSearchConfiguration {
    static final String OPEN_SEARCH_PATTERN = "[a-z0-9]+-ccs-scale-cat-opensearch";
    private final VCAPServices vcapServices;


    @SneakyThrows
    @Override
    @Bean
    public RestHighLevelClient opensearchClient() {

         SSLContext sslContext = SSLContexts.custom()
                .loadTrustMaterial(null, TrustAllStrategy.INSTANCE)
                .build();
        OpensearchCredentials opensearchCredentials = vcapServices.getOpensearch().stream()
                .filter(b -> b.getName().matches(OPEN_SEARCH_PATTERN)).findFirst().orElseThrow().getCredentials();
        String hostnameurl = opensearchCredentials.getHostname()+":"+opensearchCredentials.getPort();
        final ClientConfiguration clientConfiguration = ClientConfiguration.builder()
                .connectedTo(hostnameurl)
                .usingSsl(sslContext, NoopHostnameVerifier.INSTANCE)
                .withBasicAuth(opensearchCredentials.getUsername(),opensearchCredentials.getPassword())
                .build();

        return RestClients.create(clientConfiguration).rest();
    }
}