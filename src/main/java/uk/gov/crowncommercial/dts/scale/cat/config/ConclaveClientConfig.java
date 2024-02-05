package uk.gov.crowncommercial.dts.scale.cat.config;

import static org.springframework.http.HttpHeaders.ACCEPT;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.transport.HttpClientTransportDynamic;
import org.eclipse.jetty.io.ClientConnector;
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
public class ConclaveClientConfig {

  private final ConclaveAPIConfig conclaveAPIConfig;

  @Bean("conclaveWrapperAPIClient")
  public WebClient conclaveWrapperAPIClient(
      final OAuth2AuthorizedClientManager authorizedClientManager) {

    SslContextFactory.Client sslContextFactory = new SslContextFactory.Client(true);
    sslContextFactory.setExcludeProtocols("TLSv1.3");

    ClientConnector clientConnector = new ClientConnector();
    clientConnector.setSslContextFactory(sslContextFactory);

    HttpClient httpClient = new HttpClient(new HttpClientTransportDynamic(clientConnector));
    ClientHttpConnector jettyHttpClientConnector = new JettyClientHttpConnector(httpClient);

    return WebClient.builder().clientConnector(jettyHttpClientConnector)
        .baseUrl(conclaveAPIConfig.getBaseUrl()).defaultHeader(ACCEPT, APPLICATION_JSON_VALUE)
        .defaultHeader("x-api-key", conclaveAPIConfig.getApiKey()).build();
  }

  @Bean("conclaveIdentitiesAPIClient")
  public WebClient conclaveIdentitiesAPIClient(
      final OAuth2AuthorizedClientManager authorizedClientManager) {

    SslContextFactory.Client sslContextFactory = new SslContextFactory.Client(true);
    sslContextFactory.setExcludeProtocols("TLSv1.3");

    ClientConnector clientConnector = new ClientConnector();
    clientConnector.setSslContextFactory(sslContextFactory);

    HttpClient httpClient = new HttpClient(new HttpClientTransportDynamic(clientConnector));
    ClientHttpConnector jettyHttpClientConnector = new JettyClientHttpConnector(httpClient);

    return WebClient.builder().clientConnector(jettyHttpClientConnector)
        .baseUrl(conclaveAPIConfig.getIdentitiesBaseUrl()).defaultHeader(ACCEPT, APPLICATION_JSON_VALUE)
        .defaultHeader("x-api-key", conclaveAPIConfig.getIdentitiesApiKey()).build();
  }

}
