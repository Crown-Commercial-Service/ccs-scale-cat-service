package uk.gov.crowncommercial.dts.scale.cat.service;

import static java.util.Optional.ofNullable;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
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
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.GetCompanyDataResponse;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.ReturnCompanyInfo;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.ReturnSubUser.SubUser;

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
  private final LoadingCache<SubUserIdentity, Pair<ReturnCompanyInfo, SubUser>> jaggaerSubUserProfileCache =
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

  @SneakyThrows
  public String resolveJaggaerUserId(final String principalEmail) {
    var subUserIdentityEmail =
        new SubUserIdentity(principalEmail, getFilterPredicateEmail(principalEmail));
    return jaggaerSubUserProfileCache.get(subUserIdentityEmail).getSecond().getUserId();
  }

  @SneakyThrows
  public String resolveJaggaerBuyerCompanyId(final String principalEmail) {
    var subUserIdentityEmail =
        new SubUserIdentity(principalEmail, getFilterPredicateEmail(principalEmail));
    return jaggaerSubUserProfileCache.get(subUserIdentityEmail).getFirst().getBravoId();
  }

  // @SneakyThrows
  public SubUser resolveJaggaerUserEmail(final String principalUserId) throws ExecutionException {
    final var subUserIdentityUserId =
        new SubUserIdentity(principalUserId, getFilterPredicateUserId(principalUserId));
    return jaggaerSubUserProfileCache.get(subUserIdentityUserId).getSecond();
  }

  private CacheLoader<SubUserIdentity, Pair<ReturnCompanyInfo, SubUser>> jaggaerSubUserProfileCacheLoader() {
    return new CacheLoader<>() {

      @Override
      public Pair<ReturnCompanyInfo, SubUser> load(final SubUserIdentity subUserIdentity)
          throws Exception {
        final var getBuyerCompanyProfile = jaggaerAPIConfig.getGetBuyerCompanyProfile();
        final var endpoint = getBuyerCompanyProfile.get("endpoint");

        log.info("Calling company profiles endpoint: {}", endpoint);

        final var getCompanyDataResponse = ofNullable(
            jaggaerWebClient.get().uri(endpoint).retrieve().bodyToMono(GetCompanyDataResponse.class)
                .block(Duration.ofSeconds(jaggaerAPIConfig.getTimeoutDuration()))).orElseThrow(
                    () -> new JaggaerApplicationException(INTERNAL_SERVER_ERROR.value(),
                        "Unexpected error retrieving Jaggear company profile data"));

        if (!"0".equals(getCompanyDataResponse.getReturnCode())
            || !"OK".equals(getCompanyDataResponse.getReturnMessage())) {
          throw new JaggaerApplicationException(getCompanyDataResponse.getReturnCode(),
              getCompanyDataResponse.getReturnMessage());
        }
        log.debug("Retrieved company profile record: {}", getCompanyDataResponse);

        if (getCompanyDataResponse.getReturnCompanyData().size() != 1) {
          throw INVALID_COMPANY_PROFILE_DATA_EXCEPTION;
        }
        final var returnCompanyData = getCompanyDataResponse.getReturnCompanyData().stream()
            .findFirst().orElseThrow(() -> INVALID_COMPANY_PROFILE_DATA_EXCEPTION);
        final var subUsers = returnCompanyData.getReturnSubUser().getSubUsers();

        final var subUser = subUsers.stream().filter(subUserIdentity.getFilterPredicate())
            .findFirst()
            .orElseThrow(() -> new JaggaerApplicationException(INTERNAL_SERVER_ERROR.value(),
                "Invalid state: Jaggaer company profile sub-user data must contain exactly 1 matching record for user identity: "
                    + subUserIdentity.getIdentity()));
        log.debug("Matched sub-user record: {}", subUser);

        return Pair.of(returnCompanyData.getReturnCompanyInfo(), subUser);
      }
    };
  }

}
