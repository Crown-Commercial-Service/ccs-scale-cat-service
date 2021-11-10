package uk.gov.crowncommercial.dts.scale.cat.config;

import static org.springframework.http.HttpHeaders.ACCEPT;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.http.client.reactive.JettyClientHttpConnector;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.web.reactive.function.client.WebClient;
import lombok.RequiredArgsConstructor;

/**
 *
 */
@Configuration
@RequiredArgsConstructor
public class AgreementsServiceClientConfig {

  private final AgreementsServiceAPIConfig agreementsServiceAPIConfig;

  @Bean("agreementsServiceWebClient")
  public WebClient webClient(OAuth2AuthorizedClientManager authorizedClientManager) {
    var client = new HttpClient(new SslContextFactory.Client(true));

    ClientHttpConnector jettyHttpClientConnector = new JettyClientHttpConnector(client);

    return WebClient.builder().clientConnector(jettyHttpClientConnector)
        .baseUrl(agreementsServiceAPIConfig.getBaseUrl())
        .defaultHeader(ACCEPT, APPLICATION_JSON_VALUE)
        .defaultHeader("x-api-key", agreementsServiceAPIConfig.getApiKey()).build();
  }

}
