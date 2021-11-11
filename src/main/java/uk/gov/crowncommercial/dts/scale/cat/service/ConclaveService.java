package uk.gov.crowncommercial.dts.scale.cat.service;

import static java.time.Duration.ofSeconds;
import static java.util.Optional.ofNullable;
import static uk.gov.crowncommercial.dts.scale.cat.config.AgreementsServiceAPIConfig.KEY_URI_TEMPLATE;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;
import uk.gov.crowncommercial.dts.scale.cat.config.ConclaveAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.exception.ConclaveApplicationException;
import uk.gov.crowncommercial.dts.scale.cat.model.conclave_wrapper.generated.UserContactInfoList;
import uk.gov.crowncommercial.dts.scale.cat.model.conclave_wrapper.generated.UserProfileResponseInfo;

/**
 * Conclave Service.
 */
@Service
@RequiredArgsConstructor
public class ConclaveService {

  private final WebClient conclaveWebClient;
  private final ConclaveAPIConfig conclaveAPIConfig;

  /**
   * Get User Profile details.
   *
   * @param userId
   * @return an optional user profile (empty if not found)
   */
  public Optional<UserProfileResponseInfo> getUserProfile(final String userId) {

    final var getUserTemplateURI = conclaveAPIConfig.getGetUser().get(KEY_URI_TEMPLATE);

    return ofNullable(conclaveWebClient.get().uri(getUserTemplateURI, userId).retrieve()
        .bodyToMono(UserProfileResponseInfo.class)
        .onErrorResume(WebClientResponseException.class,
            ex -> ex.getRawStatusCode() == 404 ? Mono.empty() : Mono.error(ex))
        .block(ofSeconds(conclaveAPIConfig.getTimeoutDuration())));
  }

  /**
   * Get User Contact details.
   *
   * @param userId
   * @return
   */
  public UserContactInfoList getUserContacts(final String userId) {

    final var getUserTemplateURI = conclaveAPIConfig.getGetUserContacts().get(KEY_URI_TEMPLATE);

    return ofNullable(conclaveWebClient.get().uri(getUserTemplateURI, userId).retrieve()
        .bodyToMono(UserContactInfoList.class)
        .block(ofSeconds(conclaveAPIConfig.getTimeoutDuration())))
            .orElseThrow(() -> new ConclaveApplicationException(
                "Unexpected error retrieving User contacts from Conclave"));
  }
}
