package uk.gov.crowncommercial.dts.scale.cat.config;

import static org.springframework.http.HttpHeaders.ACCEPT;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.web.reactive.function.client.WebClient;
import lombok.RequiredArgsConstructor;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

/**
 *
 */
@Configuration
@RequiredArgsConstructor
public class AgreementsServiceClientConfig {

  private final AgreementsServiceAPIConfig agreementsServiceAPIConfig;

  @Bean("agreementsServiceWebClient")
  public WebClient webClient(final OAuth2AuthorizedClientManager authorizedClientManager) {

    // See https://github.com/reactor/reactor-netty/issues/1774
    // Critical setting seems to be the maxIdleTime, which by default is not set meaning unlimieted.
    var provider = ConnectionProvider.builder("custom-name").maxConnections(500)
        .maxIdleTime(Duration.ofSeconds(20)).maxLifeTime(Duration.ofSeconds(60))
        .pendingAcquireTimeout(Duration.ofSeconds(60)).evictInBackground(Duration.ofSeconds(120))
        .build();

    var client = HttpClient.create(provider);

    return WebClient.builder().clientConnector(new ReactorClientHttpConnector(client))
        .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
        .baseUrl(agreementsServiceAPIConfig.getBaseUrl())
        .defaultHeader(ACCEPT, APPLICATION_JSON_VALUE)
        .defaultHeader("x-api-key", agreementsServiceAPIConfig.getApiKey()).build();
  }

}
