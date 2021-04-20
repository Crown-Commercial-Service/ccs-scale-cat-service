package uk.gov.crowncommercial.dts.scale.cat.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryReactiveOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

/**
 *
 */
@Configuration
public class JaggaerConfig {

  @Bean("jaggaerWebClient")
  WebClient webClient(ReactiveClientRegistrationRepository clientRegistrations) {

    InMemoryReactiveOAuth2AuthorizedClientService clientService =
        new InMemoryReactiveOAuth2AuthorizedClientService(clientRegistrations);

    AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager authorizedClientManager =
        new AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager(clientRegistrations,
            clientService);

    ServerOAuth2AuthorizedClientExchangeFilterFunction oauth =
        new ServerOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager);
    oauth.setDefaultClientRegistrationId("myprovider");
    oauth.setDefaultClientRegistrationId("jaggaer");

    return WebClient.builder().filter(oauth).build();
  }

}
