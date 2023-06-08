package uk.gov.crowncommercial.dts.scale.cat.config;


import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.RestClients;
import org.springframework.data.elasticsearch.config.AbstractElasticsearchConfiguration;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;
import org.springframework.data.elasticsearch.repository.config.EnableReactiveElasticsearchRepositories;
import org.springframework.http.HttpHeaders;

@Configuration
@EnableElasticsearchRepositories("uk.gov.crowncommercial.dts.scale.cat.opensearch.repo")
@EnableReactiveElasticsearchRepositories("uk.gov.crowncommercial.dts.scale.cat.opensearch.reactiverepo")
public class RestClientConfig extends AbstractElasticsearchConfiguration {
    @Override
    @Bean
    public RestHighLevelClient elasticsearchClient() {
        final ClientConfiguration clientConfiguration = ClientConfiguration
                .builder()
                .connectedTo("localhost:9200")
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
