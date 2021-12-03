package uk.gov.crowncommercial.dts.scale.cat.service;

import static uk.gov.crowncommercial.dts.scale.cat.config.AgreementsServiceAPIConfig.KEY_URI_TEMPLATE;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import lombok.RequiredArgsConstructor;
import uk.gov.crowncommercial.dts.scale.cat.config.ConclaveAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.exception.ConclaveApplicationException;
import uk.gov.crowncommercial.dts.scale.cat.model.conclave_wrapper.generated.OrganisationProfileResponseInfo;
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
   * Find and return a user profile from Conclave
   *
   * @param email
   * @return an optional user profile (empty if not found)
   */
  public Optional<UserProfileResponseInfo> getUserProfile(final String email) {

    final var templateURI = conclaveAPIConfig.getGetUser().get(KEY_URI_TEMPLATE);

    try {
      return webclientWrapper.getOptionalResource(UserProfileResponseInfo.class, conclaveWebClient,
          conclaveAPIConfig.getTimeoutDuration(), templateURI, email);
    } catch (Exception e) {
      throw new ConclaveApplicationException(
          "Unexpected error retrieving User profile from Conclave");
    }
  }

  /**
   * Get User Contact details
   *
   * @param userId
   * @return
   */
  public UserContactInfoList getUserContacts(final String userId) {

    final var templateURI = conclaveAPIConfig.getGetUserContacts().get(KEY_URI_TEMPLATE);

    try {
      return webclientWrapper.getOptionalResource(UserContactInfoList.class, conclaveWebClient,
          conclaveAPIConfig.getTimeoutDuration(), templateURI, userId).orElseThrow();
    } catch (Exception e) {
      throw new ConclaveApplicationException(
          "Unexpected error retrieving User contacts from Conclave");
    }
  }

  /**
   * Find and return an organisation profile from Conclave
   *
   * @param orgId
   * @return
   */
  public Optional<OrganisationProfileResponseInfo> getOrganisation(final String orgId) {

    final var templateURI = conclaveAPIConfig.getGetOrganisation().get(KEY_URI_TEMPLATE);

    try {
      return webclientWrapper.getOptionalResource(OrganisationProfileResponseInfo.class,
          conclaveWebClient, conclaveAPIConfig.getTimeoutDuration(), templateURI, orgId);
    } catch (Exception e) {
      throw new ConclaveApplicationException(
          "Unexpected error retrieving org profile from Conclave");
    }
  }
}
