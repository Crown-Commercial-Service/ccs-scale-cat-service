package uk.gov.crowncommercial.dts.scale.cat.config;

import static org.springframework.http.HttpHeaders.ACCEPT;
import java.util.Base64;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.transport.HttpClientTransportDynamic;
import org.eclipse.jetty.io.ClientConnector;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.http.client.reactive.JettyClientHttpConnector;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.web.reactive.function.client.WebClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 *
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class JaggaerSOAPClientConfig {

  @Value("${spring.security.oauth2.client.registration.jaggaer.client-id}")
  private String clientId;

  @Value("${spring.security.oauth2.client.registration.jaggaer.client-secret}")
  private String clientSecret;

  private final JaggaerAPIConfig apiConfig;

  @Bean("jaggaerSOAPWebClient")
  public WebClient webClient(final OAuth2AuthorizedClientManager authorizedClientManager) {

    SslContextFactory.Client sslContextFactory = new SslContextFactory.Client(true);
    sslContextFactory.setExcludeProtocols("TLSv1.3");

    ClientConnector clientConnector = new ClientConnector();
    clientConnector.setSslContextFactory(sslContextFactory);

    HttpClient httpClient = new HttpClient(new HttpClientTransportDynamic(clientConnector));
    ClientHttpConnector jettyHttpClientConnector = new JettyClientHttpConnector(httpClient);

    var authHeader =
        "Basic " + Base64.getEncoder().encodeToString((clientId + ':' + clientSecret).getBytes());

    return WebClient.builder().clientConnector(jettyHttpClientConnector)
        .baseUrl(apiConfig.getBaseUrl()).defaultHeader(ACCEPT, MediaType.APPLICATION_XML_VALUE)
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_XML_VALUE)
        .defaultHeader(HttpHeaders.AUTHORIZATION, authHeader).build();
  }

}
