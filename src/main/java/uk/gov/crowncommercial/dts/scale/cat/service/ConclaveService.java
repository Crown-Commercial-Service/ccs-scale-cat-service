package uk.gov.crowncommercial.dts.scale.cat.service;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import com.rollbar.notifier.Rollbar;
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
  public static final String KEY_URI_TEMPLATE = "uriTemplate";

  private final ConclaveAPIConfig conclaveAPIConfig;
  private final WebClient conclaveWrapperAPIClient;
  private final WebClient conclaveIdentitiesAPIClient;
  private final WebclientWrapper webclientWrapper;

  @Value
  @Builder
  public static class UserContactPoints {

    String phone;
    String fax;
    String web;
    String email;
  }
 @Autowired
  Rollbar rollbar;
  /**
   * Find and return a user profile from Conclave
   *
   * @param email
   * @return an optional user profile (empty if not found)
   */
  @Cacheable(value = "conclaveCache", key = "#root.methodName + '-' + #email")
  public Optional<UserProfileResponseInfo> getUserProfile(final String email) {

    final var templateURI = conclaveAPIConfig.getGetUser().get(KEY_URI_TEMPLATE);

    try {
      return webclientWrapper.getOptionalResource(UserProfileResponseInfo.class,
          conclaveWrapperAPIClient, conclaveAPIConfig.getTimeoutDuration(), templateURI,
          email.toLowerCase());
    } catch (Exception e) {
      rollbar.error(e, "Unexpected error retrieving User profile from Conclave");
      return Optional.empty();
    }
  }
  /**
   * Get User Contact details
   *
   * @param userId
   * @return
   */
  @Cacheable(value = "conclaveCache", key = "#root.methodName + '-' + #userId")
  public UserContactInfoList getUserContacts(final String userId) {

    final var templateURI = conclaveAPIConfig.getGetUserContacts().get(KEY_URI_TEMPLATE);

    try {
      return webclientWrapper
          .getOptionalResource(UserContactInfoList.class, conclaveWrapperAPIClient,
              conclaveAPIConfig.getTimeoutDuration(), templateURI, userId.toLowerCase())
          .orElseThrow();
    } catch (Exception e) {
        rollbar.error(e, "Unexpected error retrieving User contacts from Conclave");
    }
      return null;
  }

  /**
   * Find and return an organisation profile from Conclave
   *
   * @param orgId the internal org identifier
   * @return
   */
  @Cacheable(value = "conclaveCache", key = "#root.methodName + '-' + #orgId")
  public Optional<OrganisationProfileResponseInfo> getOrganisationProfile(final String orgId) {

    final var templateURI = conclaveAPIConfig.getGetOrganisation().get(KEY_URI_TEMPLATE);

    var sanitisedOrgId = orgId.replace("US-DUNS", "US-DUN");

    try {
      return webclientWrapper.getOptionalResource(OrganisationProfileResponseInfo.class,
              conclaveWrapperAPIClient, conclaveAPIConfig.getTimeoutDuration(), templateURI, sanitisedOrgId);
    } catch (Exception e) {
        rollbar.error(e, "Unexpected error retrieving org profile from Conclave for org ID: " + sanitisedOrgId);
        return Optional.empty();
    }
  }

  /**
   * Find and return an organisation identity from CII/Conclave
   *
   * @param orgId the public identifier e.g. US-DUNS-123456789
   * @return
   */
  @Cacheable(value = "conclaveCache", key = "#root.methodName + '-' + #orgId")
  public Optional<OrganisationProfileResponseInfo> getOrganisationIdentity(final String orgId) {

    final var templateURI = conclaveAPIConfig.getGetOrganisationIdentity().get(KEY_URI_TEMPLATE);
    // Sanitise DUNS prefix for CII search..
    var sanitisedOrgId = orgId.replace("US-DUNS", "US-DUN");

    try {
      return webclientWrapper.getOptionalResource(OrganisationProfileResponseInfo.class,
          conclaveIdentitiesAPIClient, conclaveAPIConfig.getTimeoutDuration(), templateURI,
          sanitisedOrgId);
    } catch (Exception e) {
        rollbar.error(e, "Unexpected error retrieving org identity from CII for org ID: " + orgId);
        return Optional.empty();
    }
  }

  /**
   * Extracts whatever user contact info is available (may be none in which case all fields are
   * returned null)
   *
   * @param userContactInfoList
   * @return
   */
  public UserContactPoints extractUserPersonalContacts(
      final UserContactInfoList userContactInfoList) {
          try{
                var personalContactInfo = userContactInfoList.getContactPoints().stream()
                    .filter(cpi -> "PERSONAL".equals(cpi.getContactPointReason())).findFirst();

                final var userContactPoints = UserContactPoints.builder();

                if (personalContactInfo.isPresent()) {

                  personalContactInfo.get().getContacts().stream()
                      .filter(crd -> "PHONE".equals(crd.getContactType())).findFirst()
                      .ifPresent(crd -> userContactPoints.phone(crd.getContactValue()));

                  personalContactInfo.get().getContacts().stream()
                      .filter(crd -> "FAX".equals(crd.getContactType())).findFirst()
                      .ifPresent(crd -> userContactPoints.fax(crd.getContactValue()));

                  personalContactInfo.get().getContacts().stream()
                      .filter(crd -> "EMAIL".equals(crd.getContactType())).findFirst()
                      .ifPresent(crd -> userContactPoints.email(crd.getContactValue()));

                  personalContactInfo.get().getContacts().stream()
                      .filter(crd -> "WEB_ADDRESS".equals(crd.getContactType())).findFirst()
                      .ifPresent(crd -> userContactPoints.web(crd.getContactValue()));

                }
                return userContactPoints.build();
          } catch (Exception e) {
              rollbar.error(e, "Error extracting user personal contacts");
              return UserContactPoints.builder().build();
          }
  }

  public String getOrganisationIdentifer(final OrganisationProfileResponseInfo org) {
      try{
        var schemeName = org.getIdentifier().getScheme().replace("US-DUN", "US-DUNS");
        return schemeName + '-' + org.getIdentifier().getId();
      } catch (Exception e) {
          rollbar.error(e, "Error extracting organisation identifier");
          return "";
      }
  }
}
