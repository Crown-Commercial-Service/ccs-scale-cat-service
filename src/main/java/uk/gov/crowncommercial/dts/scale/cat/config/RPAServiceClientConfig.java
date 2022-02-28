package uk.gov.crowncommercial.dts.scale.cat.config;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.springframework.http.HttpHeaders.ACCEPT;
import static org.springframework.http.HttpHeaders.ACCEPT_CHARSET;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.net.URI;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.http.client.reactive.JettyClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import lombok.RequiredArgsConstructor;

/**
 *
 */
@Configuration
@RequiredArgsConstructor
public class RPAServiceClientConfig {

  private final DocumentConfig documentConfig;

  private final RPAAPIConfig rpaAPIConfig;

  @Bean("rpaServiceWebClient")
  public WebClient webClient() {

    var sslContextFactory = new SslContextFactory.Client(true);

    // SCAT-2463: https://webtide.com/openjdk-11-and-tls-1-3-issues/
    sslContextFactory.setExcludeProtocols("TLSv1.3");

    // delay
    var client = new HttpClient(sslContextFactory) {
      @Override
      public Request newRequest(final URI uri) {
        return super.newRequest(uri);
      }
    };
    ClientHttpConnector jettyHttpClientConnector = new JettyClientHttpConnector(client);

    return WebClient.builder().clientConnector(jettyHttpClientConnector).baseUrl(rpaAPIConfig.getBaseUrl())
        .defaultHeader(ACCEPT, APPLICATION_JSON_VALUE).defaultHeader(ACCEPT_CHARSET, UTF_8.name())
        .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(documentConfig.getMaxSize())).build();
  }

}