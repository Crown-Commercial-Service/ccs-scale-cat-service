package uk.gov.crowncommercial.dts.scale.cat.service;

import static java.util.Optional.ofNullable;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import java.time.Duration;
import java.util.Set;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.exception.JaggaerApplicationException;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.GetCompanyDataResponse;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.ReturnCompanyData;
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

  private final JaggaerAPIConfig jaggaerAPIConfig;
  private final WebClient jaggaerWebClient;
  private final LoadingCache<String, Pair<ReturnCompanyInfo, SubUser>> jaggaerSubUserProfileCache =
      CacheBuilder.newBuilder().maximumSize(1000).expireAfterWrite(Duration.ofMinutes(30))
          .build(jaggaerSubUserProfileCacheLoader());

  @SneakyThrows
  public String resolveJaggaerUserId(String principal) {
    return jaggaerSubUserProfileCache.get(principal).getSecond().getUserId();
  }

  @SneakyThrows
  public String resolveJaggaerBuyerCompanyId(String principal) {
    return jaggaerSubUserProfileCache.get(principal).getFirst().getBravoId();
  }

  private CacheLoader<String, Pair<ReturnCompanyInfo, SubUser>> jaggaerSubUserProfileCacheLoader() {
    return new CacheLoader<>() {

      @Override
      public Pair<ReturnCompanyInfo, SubUser> load(String principal) throws Exception {
        var getBuyerCompanyProfile = jaggaerAPIConfig.getGetBuyerCompanyProfile();
        var principalPlaceholder = getBuyerCompanyProfile.get("principalPlaceholder");
        var endpoint =
            getBuyerCompanyProfile.get("endpoint").replace(principalPlaceholder, principal);

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
        log.debug("Retrieved company profile record: {}", getCompanyDataResponse);

        if (getCompanyDataResponse.getReturnCompanyData().size() != 1) {
          throw new JaggaerApplicationException(INTERNAL_SERVER_ERROR.value(),
              "Invalid state: Jaggaer company profile data must contain exactly 1 'GURU' record");
        }
        ReturnCompanyData returnCompanyData =
            getCompanyDataResponse.getReturnCompanyData().stream().findFirst().get();
        Set<SubUser> subUsers = returnCompanyData.getReturnSubUser().getSubUsers();

        var subUser = subUsers.stream().filter(su -> principal.equalsIgnoreCase(su.getEmail()))
            .findFirst()
            .orElseThrow(() -> new JaggaerApplicationException(INTERNAL_SERVER_ERROR.value(),
                "Invalid state: Jaggaer company profile sub-user data must contain exactly 1 matching record by email for principal: "
                    + principal));
        log.debug("Matched sub-user record: {}", subUser);

        return Pair.of(returnCompanyData.getReturnCompanyInfo(), subUser);
      }
    };
  }

}
