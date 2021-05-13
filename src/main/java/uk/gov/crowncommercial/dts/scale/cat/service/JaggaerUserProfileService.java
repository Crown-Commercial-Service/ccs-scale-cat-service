package uk.gov.crowncommercial.dts.scale.cat.service;

import java.time.Duration;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.GetCompanyDataResponse;

/**
 *
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JaggaerUserProfileService {

  private final JaggaerAPIConfig jaggaerAPIConfig;
  private final WebClient jaggaerWebClient;

  public String resolveJaggaerUserId(String principal) {
    var getBuyerCompanyProfile = jaggaerAPIConfig.getGetBuyerCompanyProfile();
    var principalPlaceholder = getBuyerCompanyProfile.get("principalPlaceholder");

    // TODO: Currently only works with 'tom.bunting@...'
    var endpoint = getBuyerCompanyProfile.get("endpoint").replace(principalPlaceholder, principal);

    log.info("Calling company profiles endpoint: {}", endpoint);

    var getCompanyDataResponse =
        jaggaerWebClient.get().uri(endpoint).retrieve().bodyToMono(GetCompanyDataResponse.class)
            .block(Duration.ofSeconds(jaggaerAPIConfig.getTimeoutDuration()));

    if (!"0".equals(getCompanyDataResponse.getReturnCode())
        || !"OK".equals(getCompanyDataResponse.getReturnMessage())) {
      // TODO: Reinstate
      // throw new JaggaerApplicationException(getCompanyDataResponse.getReturnCode(),
      // getCompanyDataResponse.getReturnMessage());
    }
    log.debug("Retrieved buyer user record: {}", getCompanyDataResponse);

    // TODO: Reinstate
    // return getCompanyDataResponse.getReturnCompanyData().stream().findFirst().get()
    // .getReturnCompanyInfo().getUserId();

    return "102990";
  }

}
