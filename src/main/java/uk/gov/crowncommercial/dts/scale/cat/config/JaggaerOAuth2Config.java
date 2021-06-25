package uk.gov.crowncommercial.dts.scale.cat.config;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.springframework.http.HttpHeaders.ACCEPT;
import static org.springframework.http.HttpHeaders.ACCEPT_CHARSET;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import java.util.Arrays;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.http.client.reactive.JettyClientHttpConnector;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.endpoint.DefaultClientCredentialsTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2ClientCredentialsGrantRequest;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import lombok.RequiredArgsConstructor;

/**
 * Configure and expose a non-reactive Jaggaer {@link WebClient} instance for use in calls to
 * Jaggaer. See
 * https://docs.spring.io/spring-security/site/docs/5.2.1.RELEASE/reference/htmlsingle/#oauth2Client-webclient-servlet
 */
@Configuration
@RequiredArgsConstructor
public class JaggaerOAuth2Config {

  private final JaggaerAPIConfig jaggaerAPIConfig;
  private final JaggaerTokenResponseConverter jaggaerTokenResponseConverter;

  @Bean
  public OAuth2AccessTokenResponseClient<OAuth2ClientCredentialsGrantRequest> accessTokenResponseClient() {

    var accessTokenResponseClient = new DefaultClientCredentialsTokenResponseClient();

    var tokenResponseHttpMessageConverter = new OAuth2AccessTokenResponseHttpMessageConverter();
    tokenResponseHttpMessageConverter.setTokenResponseConverter(jaggaerTokenResponseConverter);

    var restTemplate = new RestTemplate(
        Arrays.asList(new FormHttpMessageConverter(), tokenResponseHttpMessageConverter));
    restTemplate.setErrorHandler(new OAuth2ErrorResponseErrorHandler());

    accessTokenResponseClient.setRestOperations(restTemplate);
    return accessTokenResponseClient;
  }

  @Bean
  public OAuth2AuthorizedClientManager authorizedClientManager(
      ClientRegistrationRepository clientRegistrationRepository,
      OAuth2AuthorizedClientRepository authorizedClientRepository) {

    OAuth2AuthorizedClientProvider authorizedClientProvider =
        OAuth2AuthorizedClientProviderBuilder.builder()
            .clientCredentials(
                configurer -> configurer.accessTokenResponseClient(accessTokenResponseClient()))
            .build();

    var authorizedClientManager = new DefaultOAuth2AuthorizedClientManager(
        clientRegistrationRepository, authorizedClientRepository);
    authorizedClientManager.setAuthorizedClientProvider(authorizedClientProvider);

    return authorizedClientManager;
  }

  @Bean("jaggaerWebClient")
  public WebClient webClient(OAuth2AuthorizedClientManager authorizedClientManager) {
    var oauth2Client =
        new ServletOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager);
    oauth2Client.setDefaultClientRegistrationId("jaggaer");

    // TODO: Refactor out / investigate why default netty library causes 30 second delay
    var client = new HttpClient(new SslContextFactory.Client(true));
    ClientHttpConnector jettyHttpClientConnector = new JettyClientHttpConnector(client);

    return WebClient.builder().clientConnector(jettyHttpClientConnector)
        .baseUrl(jaggaerAPIConfig.getBaseUrl()).defaultHeader(ACCEPT, APPLICATION_JSON_VALUE)
        .defaultHeader(ACCEPT_CHARSET, UTF_8.name()).apply(oauth2Client.oauth2Configuration())
        .build();
  }

}
