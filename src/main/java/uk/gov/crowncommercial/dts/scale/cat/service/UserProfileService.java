package uk.gov.crowncommercial.dts.scale.cat.service;

import static java.util.Optional.ofNullable;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig.PRINCIPAL_PLACEHOLDER;
import static uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig.DUNS_PLACEHOLDER;
import static uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig.FISCALCODE_PLACEHOLDER;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.util.UriUtils;
import uk.gov.crowncommercial.dts.scale.cat.config.ApplicationFlagsConfig;
import uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.exception.DuplicateFiscalCodeException;
import uk.gov.crowncommercial.dts.scale.cat.exception.JaggaerApplicationException;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.OrganisationMapping;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.CompanyInfo;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.GetCompanyDataResponse;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.ReturnCompanyData;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.SubUsers;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.SubUsers.SubUser;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;

/**
 * User profile service layer. Now utilises Guava's {@link LoadingCache} to provide a basic
 * time-based cache of <code>principal</code> values (i.e. the incoming JWT subject) to resolved
 * Jaggaer sub user profiles.
 * <p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserProfileService {

  public static final String ERR_MSG_FMT_ORG_NOT_FOUND =
          "Organisation id '%s' not found in organisation mappings";
  private static final JaggaerApplicationException INVALID_COMPANY_PROFILE_DATA_EXCEPTION =
      new JaggaerApplicationException(INTERNAL_SERVER_ERROR.value(),
          "Invalid state: Jaggaer company profile data must contain exactly 1 'GURU' record");

  private final JaggaerAPIConfig jaggaerAPIConfig;
  private final WebClient jaggaerWebClient;
  private final RetryableTendersDBDelegate retryableTendersDBDelegate;
  private final LoadingCache<SubUserIdentity, Pair<CompanyInfo, Optional<SubUser>>> jaggaerBuyerUserCache =
      CacheBuilder.newBuilder().maximumSize(1000).expireAfterWrite(Duration.ofMinutes(30))
          .build(jaggaerSubUserProfileCacheLoader());

  private final ApplicationFlagsConfig appFlagsConfig;

  @Value
  @EqualsAndHashCode(exclude = "filterPredicate")
  private class SubUserIdentity {

    // email, userId - any uniquely identifying property
    String identity;
    Predicate<? super SubUser> filterPredicate;
  }

  private Predicate<? super SubUser> getFilterPredicateEmailAndRightsProfile(final String email) {
    return su -> email.equalsIgnoreCase(su.getEmail())
        && jaggaerAPIConfig.getDefaultBuyerRightsProfile().equalsIgnoreCase(su.getRightsProfile());
  }

  private Predicate<? super SubUser> getFilterPredicateUserId(final String userId) {
    return su -> userId.equalsIgnoreCase(su.getUserId());
  }

  private Predicate<? super SubUser> getFilterPredicateSSOUserLogin(final String userId) {
    return su -> su.getSsoCodeData() != null && userId.equalsIgnoreCase(
        su.getSsoCodeData().getSsoCode().stream().findFirst().get().getSsoUserLogin());
  }

  /**
   * Resolves buyer users by either email address or SSO data (depending on env/config).
   *
   * @param principal the PPG user ID (email)
   * @return the buyer user profile
   */
  @SneakyThrows
  public Optional<SubUser> resolveBuyerUserProfile(final String principal) {
    if (Boolean.TRUE.equals(appFlagsConfig.getResolveBuyerUsersBySSO())) {
      return resolveBuyerUserBySSOUserLogin(principal);
    }
    return jaggaerBuyerUserCache
        .get(new SubUserIdentity(principal, getFilterPredicateEmailAndRightsProfile(principal)))
        .getSecond();
  }

  @SneakyThrows
  public Optional<SubUser> resolveBuyerUserBySSOUserLogin(final String email) {
    return jaggaerBuyerUserCache
        .get(new SubUserIdentity(email, getFilterPredicateSSOUserLogin(email))).getSecond();
  }

  /**
   * Resolves buyer user by either email address or SSO data (depending on env/config) and returns
   * their company.
   *
   * @param principal the PPG user ID (email)
   * @return the buyer user company
   */
  @SneakyThrows
  public CompanyInfo resolveBuyerUserCompany(final String principal) {
    if (Boolean.TRUE.equals(appFlagsConfig.getResolveBuyerUsersBySSO())) {
      return jaggaerBuyerUserCache
          .get(new SubUserIdentity(principal, getFilterPredicateSSOUserLogin(principal)))
          .getFirst();
    }

    return jaggaerBuyerUserCache
        .get(new SubUserIdentity(principal, getFilterPredicateEmailAndRightsProfile(principal)))
        .getFirst();
  }

  @SneakyThrows
  public Optional<SubUser> resolveBuyerUserByUserId(final String userId) {
    return jaggaerBuyerUserCache.get(new SubUserIdentity(userId, getFilterPredicateUserId(userId)))
        .getSecond();
  }

  public ReturnCompanyData getSelfServiceBuyerCompany() {
    var getBuyerCompanyProfile = jaggaerAPIConfig.getGetBuyerCompanyProfile();
    var endpoint = getBuyerCompanyProfile.get(JaggaerAPIConfig.ENDPOINT);

    log.info("Start calling Jaggaer API to get buyer company profile, endpoint : {}", endpoint);
    var getCompanyDataResponse = ofNullable(
        jaggaerWebClient.get().uri(endpoint).retrieve().bodyToMono(GetCompanyDataResponse.class)
            .block(Duration.ofSeconds(jaggaerAPIConfig.getTimeoutDuration())))
                .orElseThrow(() -> new JaggaerApplicationException(INTERNAL_SERVER_ERROR.value(),
                    "Unexpected error retrieving Jaggear company profile data"));
    log.info("Finish calling Jaggaer API to get buyer company profile, endpoint : {}", endpoint);

    if (!"0".equals(getCompanyDataResponse.getReturnCode())
        || !"OK".equals(getCompanyDataResponse.getReturnMessage())) {
      throw new JaggaerApplicationException(getCompanyDataResponse.getReturnCode(),
          getCompanyDataResponse.getReturnMessage());
    }

    if (getCompanyDataResponse.getReturnCompanyData().size() != 1) {
      throw INVALID_COMPANY_PROFILE_DATA_EXCEPTION;
    }
    return getCompanyDataResponse.getReturnCompanyData().stream().findFirst()
        .orElseThrow(() -> INVALID_COMPANY_PROFILE_DATA_EXCEPTION);
  }

  private CacheLoader<SubUserIdentity, Pair<CompanyInfo, Optional<SubUser>>> jaggaerSubUserProfileCacheLoader() {
    return new CacheLoader<>() {

      @Override
      public Pair<CompanyInfo, Optional<SubUser>> load(final SubUserIdentity subUserIdentity)
          throws Exception {
        var selfServiceBuyerCompany = getSelfServiceBuyerCompany();
        var subUser = selfServiceBuyerCompany.getReturnSubUser().getSubUsers().stream()
            .filter(subUserIdentity.getFilterPredicate()).findFirst();
        log.debug("Matched sub-user record: {}", subUser);

        return Pair.of(selfServiceBuyerCompany.getReturnCompanyInfo(), subUser);
      }
    };
  }

  /**
   * Attempt to retrieve supplier company data, first by matching sub-users by SSO user login,
   * falling back to matching the super-user (company) by SSO user login. The matching user may be
   * represented by either the company ({@link CompanyInfo} or a single {@link SubUsers}. The client
   * must determine which.
   *
   * @param ssoUserLogin
   * @return company / sub-user pair (sub-user may be empty)
   */
  public Optional<ReturnCompanyData> resolveSupplierData(final String ssoUserLogin,
      final String organisationIdentifier) {

    // Check if we have an organisation mapping record for the user's company
    var optSupplierOrgMapping =
        retryableTendersDBDelegate.findOrganisationMappingByOrganisationId(organisationIdentifier);

    if (optSupplierOrgMapping.isPresent()) {

      return getReturnCompanyData(optSupplierOrgMapping);
    }

    // Fall back on SSO super user search in case Tenders DB org mapping missing
    final var encodedSSOUserLogin = UriUtils.encode(ssoUserLogin, StandardCharsets.UTF_8);
    log.info("Start calling Jaggaer API to get supplier company profile by SSO User: {}", encodedSSOUserLogin);
    var getSupplierCompanyBySSOUserLoginEndpoint =
            jaggaerAPIConfig.getGetSupplierCompanyProfileBySSOUserLogin().get(JaggaerAPIConfig.ENDPOINT)
                    .replace(PRINCIPAL_PLACEHOLDER, encodedSSOUserLogin);
    log.info("Finish calling Jaggaer API to get supplier company profile by SSO User: {}", encodedSSOUserLogin);

    var supplierCompanyBySSO = getSupplierDataHelper(getSupplierCompanyBySSOUserLoginEndpoint);

    if (supplierCompanyBySSO.isPresent()) {
      log.warn("Tenders DB: missing org mapping for supplier org: [{}]", organisationIdentifier);
      // TODO - should we create the org mapping record here?
      return supplierCompanyBySSO;
    }
    return Optional.empty();
  }

  private Optional<ReturnCompanyData> getReturnCompanyData(Optional<OrganisationMapping> optSupplierOrgMapping) {
    var supplierOrgMapping = optSupplierOrgMapping.get();

    // Get the supplier org from Jaggaer by the bravoID
    log.info("Start calling Jaggaer API to get supplier company profile by Bravo Id: {}", supplierOrgMapping.getExternalOrganisationId());
    var getSupplierCompanyByBravoIDEndpoint = jaggaerAPIConfig
        .getGetSupplierCompanyProfileByBravoID().get(JaggaerAPIConfig.ENDPOINT).replace(
            PRINCIPAL_PLACEHOLDER, supplierOrgMapping.getExternalOrganisationId().toString());
    log.info("Finish calling Jaggaer API to get supplier company profile by Bravo Id: {}", supplierOrgMapping.getExternalOrganisationId());

    return getSupplierDataHelper(getSupplierCompanyByBravoIDEndpoint);
  }

  /**
   * Refresh the buyer user cache (for example after a new user has been created / updated)
   *
   * @param userId aka email
   */
  public void refreshBuyerCache(final String userId) {
    log.debug("Refreshing Jaggaer buyer cache for user: {}", userId);
    jaggaerBuyerUserCache
        .refresh(new SubUserIdentity(userId, getFilterPredicateEmailAndRightsProfile(userId)));
  }

  private Optional<ReturnCompanyData> getSupplierDataHelper(final String endpoint) {
    var response = ofNullable(
        jaggaerWebClient.get().uri(endpoint).retrieve().bodyToMono(GetCompanyDataResponse.class)
            .block(Duration.ofSeconds(jaggaerAPIConfig.getTimeoutDuration())))
                .orElseThrow(() -> new JaggaerApplicationException(INTERNAL_SERVER_ERROR.value(),
                    "Unexpected error retrieving Jaggear supplier company data"));

    if ("0".equals(response.getReturnCode()) && "OK".equals(response.getReturnMessage())
        && response.getTotRecords() == 1 && response.getReturnCompanyData() != null) {

      return Optional.of(response.getReturnCompanyData().stream().findFirst().get());
    }
    return Optional.empty();
  }
  
  private Set<ReturnCompanyData> getSuppliersDataHelper(final String endpoint) {
    var response = ofNullable(
        jaggaerWebClient.get().uri(endpoint).retrieve().bodyToMono(GetCompanyDataResponse.class)
            .block(Duration.ofSeconds(jaggaerAPIConfig.getTimeoutDuration())))
                .orElseThrow(() -> new JaggaerApplicationException(INTERNAL_SERVER_ERROR.value(),
                    "Unexpected error retrieving Jaggear supplier company data"));

    if ("0".equals(response.getReturnCode()) && "OK".equals(response.getReturnMessage())
        && response.getReturnCompanyData() != null) {

      return response.getReturnCompanyData();
    }
    return Collections.emptySet();
  }

  /**
   *
   * Return Supplier CompanyData for given supplier organisationId
   * @param organisationIdentifier
   * @return company
   */
  public Optional<ReturnCompanyData> resolveSupplierData(final String organisationIdentifier) {

    // Check if we have an organisation mapping record for the user's company
    var optSupplierOrgMapping =
            retryableTendersDBDelegate.findOrganisationMappingByOrganisationId(organisationIdentifier);

    if (optSupplierOrgMapping.isPresent()) {

      return getReturnCompanyData(optSupplierOrgMapping);
    }
    return Optional.empty();
  }

  /**
  *
  * Return Supplier CompanyData for given supplier duns number
  * @param concalveIdentifier
  * @return company
  */
 public Optional<ReturnCompanyData> getSupplierDataByDUNSNumber(final String concalveIdentifier) {
     // Get the supplier org from Jaggaer by the DUNS Number
   log.info("Start calling Jaggaer API to get company profile by DUNS Number: {}", concalveIdentifier);
   var getSupplierCompanyByDUNSNumberEndpoint = jaggaerAPIConfig
           .getGetCompanyProfileByDUNSNumber().get(JaggaerAPIConfig.ENDPOINT).replace(
                   DUNS_PLACEHOLDER, concalveIdentifier);
   log.info("Finish calling Jaggaer API to get company profile by DUNS Number: {}", concalveIdentifier);
   return getSupplierDataHelper(getSupplierCompanyByDUNSNumberEndpoint);
 }
 
 /**
 *
 * Return Supplier CompanyData for given supplier ficalcode (COH-Number)
 * @param concalveIdentifier
 * @return company
 */
 public Optional<ReturnCompanyData> getSupplierDataByFiscalCode(final String concalveIdentifier) {
   var getSupplierCompanyByFiscalCodeEndpoint = jaggaerAPIConfig.getGetCompanyProfileByFiscalCode()
       .get(JaggaerAPIConfig.ENDPOINT).replace(FISCALCODE_PLACEHOLDER, concalveIdentifier);
   log.info("Start calling Jaggaer API to get company profile by Fiscal Code: {}", concalveIdentifier);
   var supplierSuperUserData = getSuppliersDataHelper(getSupplierCompanyByFiscalCodeEndpoint);
   log.info("Finish calling Jaggaer API to get company profile by Fiscal Code: {}", concalveIdentifier);
   if (supplierSuperUserData.size() > 1) {
     throw new DuplicateFiscalCodeException(
         "Duplicate fiscal code " + concalveIdentifier + " found in jaggaer");
   }
   return supplierSuperUserData.stream().findFirst();

 }

  public Optional<ReturnCompanyData> resolveCompanyProfileData(final String organisationIdentifier) {

    // Check if we have an organisation mapping record for the user's company
    var optSupplierOrgMapping =
            retryableTendersDBDelegate.findOrganisationMappingByOrganisationId(organisationIdentifier);

    if (optSupplierOrgMapping.isEmpty()) {
      throw new IllegalArgumentException(
              String.format(ERR_MSG_FMT_ORG_NOT_FOUND, organisationIdentifier));
    }else{
      var supplierOrgMapping = optSupplierOrgMapping.get();
      // Get the supplier org from Jaggaer by the bravoID
      log.info("Start calling Jaggaer API to get company profile by Bravo Id: {}", supplierOrgMapping.getExternalOrganisationId());
      var getSupplierCompanyByBravoIDEndpoint = jaggaerAPIConfig.getGetCompanyProfileByBravoID()
              .get(JaggaerAPIConfig.ENDPOINT).replace(
                      PRINCIPAL_PLACEHOLDER, supplierOrgMapping.getExternalOrganisationId().toString());
      log.info("Finish calling Jaggaer API to get company profile by Bravo Id: {}", supplierOrgMapping.getExternalOrganisationId());

      return getSupplierDataHelper(getSupplierCompanyByBravoIDEndpoint);
    }
  }

}
