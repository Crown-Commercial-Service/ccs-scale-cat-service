package uk.gov.crowncommercial.dts.scale.cat.config;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.springframework.http.HttpHeaders.ACCEPT;
import static org.springframework.http.HttpHeaders.ACCEPT_CHARSET;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.dynamic.HttpClientTransportDynamic;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.io.ClientConnector;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.http.client.reactive.JettyClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 *
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class RPAServiceClientConfig {

  private final DocumentConfig documentConfig;

  private final RPAAPIConfig rpaAPIConfig;

  @Bean("rpaServiceWebClient")
  public WebClient webClient() {

    SslContextFactory.Client sslContextFactory = new SslContextFactory.Client(true);
    sslContextFactory.setExcludeProtocols("TLSv1.3");

    ClientConnector clientConnector = new ClientConnector();
    clientConnector.setSslContextFactory(sslContextFactory);

    HttpClient httpClient = new HttpClient(new HttpClientTransportDynamic(clientConnector)) {
      @Override
      public Request newRequest(final URI uri) {
        return enhance(super.newRequest(uri));
      }
    };

    ClientHttpConnector jettyHttpClientConnector = new JettyClientHttpConnector(httpClient);

    return WebClient.builder().clientConnector(jettyHttpClientConnector)
        .baseUrl(rpaAPIConfig.getBaseUrl()).defaultHeader(ACCEPT, APPLICATION_JSON_VALUE)
        .defaultHeader(ACCEPT_CHARSET, UTF_8.name())
        .codecs(
            configurer -> configurer.defaultCodecs().maxInMemorySize(documentConfig.getMaxSize()))
        .build();
  }

  /**
   * See <a>https://www.baeldung.com/spring-log-webclient-calls<a> and
   * <a>https://stackoverflow.com/a/64343794/2509595</a> for details
   *
   * @param inboundRequest
   * @return the enhanced request with logging events on request/response
   */
  private Request enhance(final Request inboundRequest) {
    var sbLog = new StringBuilder();
    // Request Logging
    inboundRequest.onRequestBegin(request -> sbLog.append("Request: \n").append("URI: ")
        .append(request.getURI()).append("\n").append("Method: ").append(request.getMethod()));
    inboundRequest.onRequestHeaders(request -> {
      sbLog.append("\nHeaders:\n");
      for (HttpField header : request.getHeaders()) {
        if (!"Authorization".equalsIgnoreCase(header.getName())) {
          sbLog.append("\t\t" + header.getName() + " : " + header.getValue() + "\n");
        } else {
          sbLog.append("\t\tAuthorization : Bearer #####\n");
        }
      }
    });
    // TODO: Make request body logging configurable. Commented out for now as it causes java heap
    // OOM when uploading large files.
    // inboundRequest.onRequestContent((request, content) -> sbLog.append("Body: \n\t")
    // .append(StandardCharsets.UTF_8.decode(content).toString()));
    sbLog.append("\n");

    // Response Logging
    inboundRequest.onResponseBegin(response -> sbLog.append("Response:\n").append("Status: ")
        .append(response.getStatus()).append("\n"));
    inboundRequest.onResponseHeaders(response -> {
      sbLog.append("Headers:\n");
      for (HttpField header : response.getHeaders()) {
        sbLog.append("\t\t" + header.getName() + " : " + header.getValue() + "\n");
      }
    });
    inboundRequest.onResponseContent((response, content) -> {
      var bufferAsString = StandardCharsets.UTF_8.decode(content).toString();
      sbLog.append("Response Body:\n" + bufferAsString);
    });

    // Add actual log invocation
    log.debug("HTTP ->\n");
    inboundRequest.onRequestSuccess(request -> log.debug(sbLog.toString()));
    inboundRequest.onResponseSuccess(response -> log.debug(sbLog.toString()));

    // Return original request
    return inboundRequest;
  }

}
