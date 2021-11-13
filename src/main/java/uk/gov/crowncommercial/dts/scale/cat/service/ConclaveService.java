package uk.gov.crowncommercial.dts.scale.cat.service;

import static uk.gov.crowncommercial.dts.scale.cat.config.AgreementsServiceAPIConfig.KEY_URI_TEMPLATE;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import lombok.RequiredArgsConstructor;
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

  private final ConclaveAPIConfig conclaveAPIConfig;
  private final WebClient conclaveWebClient;
  private final WebclientWrapper webclientWrapper;

  /**
   * Finds and returns a user profile from Conclave.
   *
   * @param email
   * @return an optional user profile (empty if not found)
   */
  public Optional<UserProfileResponseInfo> getUserProfile(final String email) {

    final var getUserTemplateURI = conclaveAPIConfig.getGetUser().get(KEY_URI_TEMPLATE);

    try {
      return webclientWrapper.getOptionalResource(UserProfileResponseInfo.class, conclaveWebClient,
          getUserTemplateURI, email);
    } catch (Exception e) {
      throw new ConclaveApplicationException(
          "Unexpected error retrieving User profile from Conclave");
    }
  }

  /**
   * Get User Contact details.
   *
   * @param userId
   * @return
   */
  public UserContactInfoList getUserContacts(final String userId) {

    final var getUserTemplateURI = conclaveAPIConfig.getGetUserContacts().get(KEY_URI_TEMPLATE);

    try {
      return webclientWrapper.getOptionalResource(UserContactInfoList.class, conclaveWebClient,
          getUserTemplateURI, userId).orElseThrow();
    } catch (Exception e) {
      throw new ConclaveApplicationException(
          "Unexpected error retrieving User contacts from Conclave");
    }
  }
}
