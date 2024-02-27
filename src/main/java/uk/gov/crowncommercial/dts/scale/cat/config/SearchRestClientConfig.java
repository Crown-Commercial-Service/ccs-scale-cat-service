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
import org.springframework.core.env.Environment;
import uk.gov.crowncommercial.dts.scale.cat.config.paas.OpensearchCredentials;
import uk.gov.crowncommercial.dts.scale.cat.config.paas.VCAPServices;

import javax.net.ssl.SSLContext;
import java.util.Arrays;

@Configuration

@Data
public class SearchRestClientConfig extends AbstractOpenSearchConfiguration {
    static final String OPEN_SEARCH_PATTERN = "[a-z0-9]+-ccs-scale-cat-opensearch";
    private final VCAPServices vcapServices;

    @Autowired
    Environment environment;


    @SneakyThrows
    @Override
    @Bean
    public RestHighLevelClient opensearchClient() {
        ClientConfiguration clientConfiguration;
         SSLContext sslContext = SSLContexts.custom()
                .loadTrustMaterial(null, TrustAllStrategy.INSTANCE)
                .build();
        OpensearchCredentials opensearchCredentials = vcapServices.getOpensearch().stream()
                .filter(b -> b.getName().matches(OPEN_SEARCH_PATTERN)).findFirst().orElseThrow().getCredentials();
        String hostnameurl = opensearchCredentials.getHostname()+":"+opensearchCredentials.getPort();
        ClientConfiguration.MaybeSecureClientConfigurationBuilder clientConfigurationBuilder = ClientConfiguration.builder()
            .connectedTo(hostnameurl);

        if(Arrays.stream(environment.getActiveProfiles()).noneMatch(profile -> profile.contains("local")))
        {
            // Only add basic auth if configured
            if(opensearchCredentials.getUsername() != null && opensearchCredentials.getUsername().isPresent() && opensearchCredentials.getPassword() != null && opensearchCredentials.getPassword().isPresent()) {
                clientConfigurationBuilder.withBasicAuth(opensearchCredentials.getUsername().get(), opensearchCredentials.getPassword().get());
            }
            // Enforce use of SSL
            clientConfigurationBuilder.usingSsl(sslContext, NoopHostnameVerifier.INSTANCE);
        }

        clientConfiguration = clientConfigurationBuilder.build();

        return RestClients.create(clientConfiguration).rest();
    }
}
