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
public class DocumentUploadClientConfig {

  private final DocumentUploadAPIConfig documentUploadAPIConfig;

  @Bean("docUploadSvcUploadWebclient")
  public WebClient uploadWebClient(final OAuth2AuthorizedClientManager authorizedClientManager) {

    SslContextFactory.Client sslContextFactory = new SslContextFactory.Client(true);
    sslContextFactory.setExcludeProtocols("TLSv1.3");

    ClientConnector clientConnector = new ClientConnector();
    clientConnector.setSslContextFactory(sslContextFactory);

    HttpClient httpClient = new HttpClient(new HttpClientTransportDynamic(clientConnector));
    ClientHttpConnector jettyHttpClientConnector = new JettyClientHttpConnector(httpClient);

    return WebClient.builder().clientConnector(jettyHttpClientConnector)
        .baseUrl(documentUploadAPIConfig.getUploadBaseUrl())
        .defaultHeader(ACCEPT, APPLICATION_JSON_VALUE)
        .defaultHeader("x-api-key", documentUploadAPIConfig.getApiKey()).build();
  }

  @Bean("docUploadSvcGetWebclient")
  public WebClient getWebClient(final OAuth2AuthorizedClientManager authorizedClientManager) {

    SslContextFactory.Client sslContextFactory = new SslContextFactory.Client(true);
    sslContextFactory.setExcludeProtocols("TLSv1.3");

    ClientConnector clientConnector = new ClientConnector();
    clientConnector.setSslContextFactory(sslContextFactory);

    HttpClient httpClient = new HttpClient(new HttpClientTransportDynamic(clientConnector));
    ClientHttpConnector jettyHttpClientConnector = new JettyClientHttpConnector(httpClient);

    return WebClient.builder().clientConnector(jettyHttpClientConnector)
        .baseUrl(documentUploadAPIConfig.getGetBaseUrl())
        .defaultHeader(ACCEPT, APPLICATION_JSON_VALUE)
        .defaultHeader("x-api-key", documentUploadAPIConfig.getApiKey()).build();
  }

}
