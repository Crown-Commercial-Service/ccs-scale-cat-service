package uk.gov.crowncommercial.dts.scale.cat.service;

import static java.util.Optional.ofNullable;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig.PRINCIPAL_PLACEHOLDER;
import java.time.Duration;
import java.util.Optional;
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
import uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.exception.JaggaerApplicationException;
import uk.gov.crowncommercial.dts.scale.cat.exception.TendersDBDataException;
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
 * TODO: Needs tests, but as much of this may change once Jaggaer is integrated with SSO will wait
 * and see what the final architecture looks like
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserProfileService {

  private static final JaggaerApplicationException INVALID_COMPANY_PROFILE_DATA_EXCEPTION =
      new JaggaerApplicationException(INTERNAL_SERVER_ERROR.value(),
          "Invalid state: Jaggaer company profile data must contain exactly 1 'GURU' record");

  private final JaggaerAPIConfig jaggaerAPIConfig;
  private final WebClient jaggaerWebClient;
  private final RetryableTendersDBDelegate retryableTendersDBDelegate;
  private final LoadingCache<SubUserIdentity, Pair<CompanyInfo, Optional<SubUser>>> jaggaerBuyerUserCache =
      CacheBuilder.newBuilder().maximumSize(1000).expireAfterWrite(Duration.ofMinutes(30))
          .build(jaggaerSubUserProfileCacheLoader());

  @Value
  @EqualsAndHashCode(exclude = "filterPredicate")
  private class SubUserIdentity {

    // email, userId - any uniquely identifying property
    String identity;
    Predicate<? super SubUser> filterPredicate;
  }

  private Predicate<? super SubUser> getFilterPredicateEmail(final String email) {
    return su -> email.equalsIgnoreCase(su.getEmail());
  }

  private Predicate<? super SubUser> getFilterPredicateUserId(final String userId) {
    return su -> userId.equalsIgnoreCase(su.getUserId());
  }

  private Predicate<? super SubUser> getFilterPredicateSSOUserLogin(final String userId) {
    return su -> su.getSsoCodeData() != null && userId.equalsIgnoreCase(
        su.getSsoCodeData().getSsoCode().stream().findFirst().get().getSsoUserLogin());
  }

  /**
   * @deprecated Use {@link #resolveBuyerUserBySSOUserLogin(String)} instead.
   *
   * @param email
   * @return
   */
  @SneakyThrows
  @Deprecated(forRemoval = true)
  public Optional<SubUser> resolveBuyerUserByEmail(final String email) {
    return jaggaerBuyerUserCache.get(new SubUserIdentity(email, getFilterPredicateEmail(email)))
        .getSecond();
  }

  @SneakyThrows
  public Optional<SubUser> resolveBuyerUserBySSOUserLogin(final String email) {
    return jaggaerBuyerUserCache
        .get(new SubUserIdentity(email, getFilterPredicateSSOUserLogin(email))).getSecond();
  }

  /**
   * @deprecated Use {@link #resolveBuyerCompanyBySSOUserLogin(String)} instead.
   *
   * @param email
   * @return
   */
  @SneakyThrows
  @Deprecated(forRemoval = true)
  public CompanyInfo resolveBuyerCompanyByEmail(final String email) {
    return jaggaerBuyerUserCache.get(new SubUserIdentity(email, getFilterPredicateEmail(email)))
        .getFirst();
  }

  @SneakyThrows
  public CompanyInfo resolveBuyerCompanyBySSOUserLogin(final String email) {
    return jaggaerBuyerUserCache
        .get(new SubUserIdentity(email, getFilterPredicateSSOUserLogin(email))).getFirst();
  }

  @SneakyThrows
  public Optional<SubUser> resolveBuyerUserByUserId(final String userId) {
    return jaggaerBuyerUserCache.get(new SubUserIdentity(userId, getFilterPredicateUserId(userId)))
        .getSecond();
  }

  private CacheLoader<SubUserIdentity, Pair<CompanyInfo, Optional<SubUser>>> jaggaerSubUserProfileCacheLoader() {
    return new CacheLoader<>() {

      @Override
      public Pair<CompanyInfo, Optional<SubUser>> load(final SubUserIdentity subUserIdentity)
          throws Exception {
        var getBuyerCompanyProfile = jaggaerAPIConfig.getGetBuyerCompanyProfile();
        var endpoint = getBuyerCompanyProfile.get(JaggaerAPIConfig.ENDPOINT);

        log.info("Calling company profiles endpoint: {}", endpoint);

        var getCompanyDataResponse = ofNullable(
            jaggaerWebClient.get().uri(endpoint).retrieve().bodyToMono(GetCompanyDataResponse.class)
                .block(Duration.ofSeconds(jaggaerAPIConfig.getTimeoutDuration()))).orElseThrow(
                    () -> new JaggaerApplicationException(INTERNAL_SERVER_ERROR.value(),
                        "Unexpected error retrieving Jaggear company profile data"));

        if (!"0".equals(getCompanyDataResponse.getReturnCode())
            || !"OK".equals(getCompanyDataResponse.getReturnMessage())) {
          throw new JaggaerApplicationException(getCompanyDataResponse.getReturnCode(),
              getCompanyDataResponse.getReturnMessage());
        }

        if (getCompanyDataResponse.getReturnCompanyData().size() != 1) {
          throw INVALID_COMPANY_PROFILE_DATA_EXCEPTION;
        }
        var returnCompanyData = getCompanyDataResponse.getReturnCompanyData().stream().findFirst()
            .orElseThrow(() -> INVALID_COMPANY_PROFILE_DATA_EXCEPTION);

        var subUser = returnCompanyData.getReturnSubUser().getSubUsers().stream()
            .filter(subUserIdentity.getFilterPredicate()).findFirst();
        log.debug("Matched sub-user record: {}", subUser);

        return Pair.of(returnCompanyData.getReturnCompanyInfo(), subUser);
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

    if (!optSupplierOrgMapping.isPresent()) {
      return Optional.empty();
    }
    var supplierOrgMapping = optSupplierOrgMapping.get();

    // Get the supplier org from Jaggaer by the bravoID
    var getSupplierCompanyEndpoint =
        jaggaerAPIConfig.getGetSupplierCompanyProfile().get(JaggaerAPIConfig.ENDPOINT).replace(
            PRINCIPAL_PLACEHOLDER, supplierOrgMapping.getExternalOrganisationId().toString());

    var supplierCompany = getSupplierDataHelper(getSupplierCompanyEndpoint);
    if (supplierCompany.isPresent()) {
      return supplierCompany;
    } else {
      throw new TendersDBDataException("TODO - invalid supplier org mapping");
    }
  }

  /**
   * Refresh the buyer user cache (for example after a new user has been created / updated)
   *
   * @param userId aka email
   */
  public void refreshBuyerCache(final String userId) {
    log.debug("Refreshing Jaggaer buyer cache for user: {}", userId);
    jaggaerBuyerUserCache.refresh(new SubUserIdentity(userId, getFilterPredicateEmail(userId)));
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

}
