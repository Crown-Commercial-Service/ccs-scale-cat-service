package uk.gov.crowncommercial.dts.scale.cat.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 *
 */
@Configuration
public class JaggaerConfig {

  private final RestTemplate restTemplate;

  public JaggaerConfig(@Value("${JAGGAER_URL:http://localhost:9010}") final String jaggaerUrl,
      @Value("${JAGGAER_CLIENT_ID:abc123}") final String jaggaerClientId) {

    restTemplate = new RestTemplateBuilder().rootUri(jaggaerUrl)
        .defaultHeader("x-api-key", jaggaerClientId).build();
  }

  // @Bean
  // protected OAuth2ProtectedResourceDetails oauth2Resource() {
  // ClientCredentialsResourceDetails clientCredentialsResourceDetails =
  // new ClientCredentialsResourceDetails();
  // clientCredentialsResourceDetails.setAccessTokenUri(tokenUrl);
  // clientCredentialsResourceDetails.setClientId(clientId);
  // clientCredentialsResourceDetails.setClientSecret(clientSecret);
  // clientCredentialsResourceDetails.setGrantType("client_credentials"); // this depends on your
  // // specific OAuth2 server
  // clientCredentialsResourceDetails.setAuthenticationScheme(AuthenticationScheme.header); // this
  // // again
  // // depends
  // // on the
  // // OAuth2
  // // server
  // // specifications
  // return clientCredentialsResourceDetails;
  // }
  //
  // @Bean
  // public OAuth2RestTemplate oauth2RestTemplate() {
  // AccessTokenRequest atr = new DefaultAccessTokenRequest();
  // OAuth2RestTemplate oauth2RestTemplate =
  // new OAuth2RestTemplate(oauth2Resource(), new DefaultOAuth2ClientContext(atr));
  // oauth2RestTemplate.setRequestFactory(new HttpComponentsClientHttpRequestFactory());
  // return oauth2RestTemplate;
  // }

  @Bean("jaggaerRestTemplate")
  public RestTemplate jaggaerRestTemplate() {
    return restTemplate;
  }

}
