package uk.gov.crowncommercial.dts.scale.cat.service;

import static java.util.Optional.ofNullable;
import java.time.Duration;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;
import uk.gov.crowncommercial.dts.scale.cat.config.ConclaveAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.model.conclave_wrapper.generated.UserProfileResponseInfo;

/**
 *
 */
@Service
@RequiredArgsConstructor
public class ConclaveWrapperApiService {

  private final WebClient conclaveWebClient;
  private final ConclaveAPIConfig conclaveAPIConfig;

  /**
   * Finds and returns a user profile from Conclave.
   *
   * @param email
   * @return an optional user profile (empty if not found)
   */
  public Optional<UserProfileResponseInfo> getUserDetails(final String email) {

    final var getUserProfileUri =
        conclaveAPIConfig.getGetUser().get(ConclaveAPIConfig.KEY_URI_TEMPLATE);

    return ofNullable(conclaveWebClient.get().uri(getUserProfileUri, email).retrieve()
        .bodyToMono(UserProfileResponseInfo.class)
        .onErrorResume(WebClientResponseException.class,
            ex -> ex.getRawStatusCode() == 404 ? Mono.empty() : Mono.error(ex))
        .block(Duration.ofSeconds(conclaveAPIConfig.getTimeoutDuration())));
  }
}
