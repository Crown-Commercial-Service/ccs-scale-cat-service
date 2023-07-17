package uk.gov.crowncommercial.dts.scale.cat.config;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.springframework.http.HttpHeaders.ACCEPT;
import static org.springframework.http.HttpHeaders.ACCEPT_CHARSET;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.HttpHeaders.WWW_AUTHENTICATE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.dynamic.HttpClientTransportDynamic;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.io.ClientConnector;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.http.client.reactive.JettyClientHttpConnector;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.endpoint.DefaultClientCredentialsTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2ClientCredentialsGrantRequest;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Configure and expose a non-reactive Jaggaer {@link WebClient} instance for use in calls to
 * Jaggaer. See
 * https://docs.spring.io/spring-security/site/docs/5.2.1.RELEASE/reference/htmlsingle/#oauth2Client-webclient-servlet
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class JaggaerClientConfig {

  private final JaggaerAPIConfig jaggaerAPIConfig;
  private final JaggaerTokenResponseConverter jaggaerTokenResponseConverter;
  private final DocumentConfig documentConfig;

  @Bean
  public OAuth2AccessTokenResponseClient<OAuth2ClientCredentialsGrantRequest> accessTokenResponseClient() {

    var accessTokenResponseClient = new DefaultClientCredentialsTokenResponseClient();

    var tokenResponseHttpMessageConverter = new OAuth2AccessTokenResponseHttpMessageConverter();
    tokenResponseHttpMessageConverter
        .setAccessTokenResponseConverter(jaggaerTokenResponseConverter);

    var restTemplate = new RestTemplate(
        Arrays.asList(new FormHttpMessageConverter(), tokenResponseHttpMessageConverter));
    restTemplate.setErrorHandler(new OAuth2ErrorResponseErrorHandler());

    accessTokenResponseClient.setRestOperations(restTemplate);
    return accessTokenResponseClient;
  }

  @Bean
  public OAuth2AuthorizedClientManager authorizedClientManager(
      final ClientRegistrationRepository clientRegistrationRepository) {

    var authorizedClientProvider = OAuth2AuthorizedClientProviderBuilder.builder()
        .clientCredentials(
            configurer -> configurer.accessTokenResponseClient(accessTokenResponseClient()))
        .build();

    var authorizedClientService =
        new InMemoryOAuth2AuthorizedClientService(clientRegistrationRepository);

    var authorizedClientManager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(
        clientRegistrationRepository, authorizedClientService);
    authorizedClientManager.setAuthorizedClientProvider(authorizedClientProvider);

    return authorizedClientManager;
  }

  @Bean("jaggaerWebClient")
  public WebClient webClient(final OAuth2AuthorizedClientManager authorizedClientManager) {
    var oauth2Client =
        new ServletOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager);
    oauth2Client.setDefaultClientRegistrationId("jaggaer");

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
    
    httpClient.setIdleTimeout(Duration.ofSeconds(jaggaerAPIConfig.getTimeoutDuration()).toMillis());

    httpClient.setIdleTimeout(Duration.ofSeconds(jaggaerAPIConfig.getTimeoutDuration()).toMillis());

    return WebClient.builder().clientConnector(jettyHttpClientConnector)
        .filter(buildResponseHeaderFilterFunction()).baseUrl(jaggaerAPIConfig.getBaseUrl())
        .defaultHeader(ACCEPT, APPLICATION_JSON_VALUE).defaultHeader(ACCEPT_CHARSET, UTF_8.name())
        .apply(oauth2Client.oauth2Configuration())
        .codecs(
            configurer -> configurer.defaultCodecs().maxInMemorySize(documentConfig.getMaxSize()))
        .build();
  }

  /**
   * SCC-517
   *
   * <p>
   * Fixes Jaggaer REST API response for 401 Unauthorized (issues are invalid content-type and
   * missing WWW-Authenticate header)
   *
   * @return the configured filter function to mutate the response
   */
  private ExchangeFilterFunction buildResponseHeaderFilterFunction() {
    return ExchangeFilterFunction.ofResponseProcessor(clientResponse -> {

      var clientResponseMutator = clientResponse.mutate();

      // Standardise 401 response
      if (clientResponse.statusCode() == HttpStatus.UNAUTHORIZED) {

        if (clientResponse.headers().header(WWW_AUTHENTICATE).isEmpty()) {
          log.debug("Jaggaer 401 - injecting WWW-Authenticate header");
          clientResponseMutator.header(WWW_AUTHENTICATE,
              jaggaerAPIConfig.getHeaderValueWWWAuthenticate());
        }

        if (clientResponse.headers().header(CONTENT_TYPE)
            .contains(jaggaerAPIConfig.getHeaderValueInvalidContentType())) {
          log.debug("Jaggaer 401 - correcting Content-Type header");
          clientResponseMutator.headers(httpHeaders -> {
            httpHeaders.remove(CONTENT_TYPE);
            httpHeaders.add(CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
          });
        }
      }

      return Mono.just(clientResponseMutator.build());
    });
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
    log.trace("HTTP ->\n");
    inboundRequest.onRequestSuccess(request -> log.trace(sbLog.toString()));
    inboundRequest.onResponseSuccess(response -> log.trace(sbLog.toString()));

    // Return original request
    return inboundRequest;
  }

}
