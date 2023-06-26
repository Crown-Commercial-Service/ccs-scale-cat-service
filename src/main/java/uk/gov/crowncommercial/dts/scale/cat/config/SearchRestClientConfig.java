package uk.gov.crowncommercial.dts.scale.cat.config;

import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.RestClients;
import org.springframework.data.elasticsearch.config.AbstractElasticsearchConfiguration;
import org.springframework.http.HttpHeaders;


@Configuration
public class SearchRestClientConfig extends AbstractElasticsearchConfiguration {
    @Override
    @Bean
    public RestHighLevelClient elasticsearchClient() {
        final ClientConfiguration clientConfiguration = ClientConfiguration
                .builder()
                .connectedTo("prod-lon-610c43e8-5115-4d99-a7b0-8418fe4dbf5a-paas-cf-prod.aivencloud.com:19676")
                //                .withDefaultHeaders(getDefault())
                .build();

        return RestClients.create(clientConfiguration).rest();
    }
    private HttpHeaders getDefault() {
        HttpHeaders header = new HttpHeaders();
        header.add(HttpHeaders.ACCEPT, "application/vnd.elasticsearch+json;compatible-with=8");
        header.add(HttpHeaders.CONTENT_TYPE, "application/vnd.elasticsearch+json;compatible-with=8");
        return header;
    }
}
