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
  public WebClient webClient(final OAuth2AuthorizedClientManager authorizedClientManager) {

    var sslContextFactory = new SslContextFactory.Client(true);

    // SCAT-2463: https://webtide.com/openjdk-11-and-tls-1-3-issues/
    sslContextFactory.setExcludeProtocols("TLSv1.3");

    ClientHttpConnector jettyHttpClientConnector =
        new JettyClientHttpConnector(new HttpClient(sslContextFactory));

    return WebClient.builder().clientConnector(jettyHttpClientConnector)
        .baseUrl(agreementsServiceAPIConfig.getBaseUrl())
        .defaultHeader(ACCEPT, APPLICATION_JSON_VALUE)
        .defaultHeader("x-api-key", agreementsServiceAPIConfig.getApiKey()).build();
  }

}
