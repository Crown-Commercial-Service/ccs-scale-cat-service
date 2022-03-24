package uk.gov.crowncommercial.dts.scale.cat.service;

import static java.util.Optional.ofNullable;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import java.io.IOException;
import java.nio.charset.Charset;
import java.time.Duration;
import javax.annotation.PostConstruct;
import org.apache.commons.io.IOUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.exception.JaggaerApplicationException;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.UpdateSubUserSSO;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.UpdateSuperUserSSO;

/**
 *
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JaggaerSOAPService {

  static final String RESPONSE_OK = "COMPANY: OK; SUBUSER: OK";
  static final String PLACEHOLDER_COMPANY_BRAVO_ID = "${COMPANY_BRAVO_ID}";
  static final String PLACEHOLDER_SUB_USER_LOGIN = "${SUB_USER_LOGIN}";
  static final String PLACEHOLDER_SUB_USER_SSO_LOGIN = "${SUB_USER_SSO_LOGIN}";
  static final String PLACEHOLDER_SUPER_USER_SSO_LOGIN = "${SUPER_USER_SSO_LOGIN}";

  private final WebClient jaggaerSOAPWebClient;
  private final JaggaerAPIConfig apiConfig;

  private String updateSubUserSSOTemplate;
  private String updateSuperUserSSOTemplate;

  @PostConstruct
  void init() throws IOException {
    updateSubUserSSOTemplate =
        IOUtils.resourceToString("/jaggaer-soap/UpdateSubUserSSO.xml", Charset.defaultCharset());
    updateSuperUserSSOTemplate =
        IOUtils.resourceToString("/jaggaer-soap/UpdateSuperUserSSO.xml", Charset.defaultCharset());
  }

  /**
   * Updates a Jaggaer sub-user's SSO data
   *
   * @param updateSubUserSSO
   */
  public void updateSubUserSSO(final UpdateSubUserSSO updateSubUserSSO) {
    updateUserSSO(
        updateSubUserSSOTemplate
            .replace(PLACEHOLDER_COMPANY_BRAVO_ID, updateSubUserSSO.getCompanyBravoID())
            .replace(PLACEHOLDER_SUB_USER_LOGIN, updateSubUserSSO.getSubUserLogin())
            .replace(PLACEHOLDER_SUB_USER_SSO_LOGIN, updateSubUserSSO.getSubUserSSOLogin()),
        updateSubUserSSO.getSubUserLogin());
  }

  /**
   * Updates a Jaggaer super-user's SSO data
   *
   * @param updateSuperUserSSO
   */
  public void updateSuperUserSSO(final UpdateSuperUserSSO updateSuperUserSSO) {
    updateUserSSO(
        updateSuperUserSSOTemplate
            .replace(PLACEHOLDER_COMPANY_BRAVO_ID, updateSuperUserSSO.getCompanyBravoID())
            .replace(PLACEHOLDER_SUPER_USER_SSO_LOGIN, updateSuperUserSSO.getSupserUserSSOLogin()),
        updateSuperUserSSO.getSupserUserSSOLogin());
  }

  private void updateUserSSO(final String requestBody, final String userId) {
    final var updateSSOResponse = ofNullable(jaggaerSOAPWebClient.post()
        .uri(apiConfig.getSoap().getProfileManagementEndpoint()).bodyValue(requestBody).retrieve()
        .bodyToMono(String.class).block(Duration.ofSeconds(apiConfig.getTimeoutDuration())))
            .orElseThrow(() -> new JaggaerApplicationException(INTERNAL_SERVER_ERROR.value(),
                "Unexpected error updating SSO data"));

    if (!StringUtils.hasText(updateSSOResponse) || !updateSSOResponse.contains(RESPONSE_OK)) {
      throw new JaggaerApplicationException(INTERNAL_SERVER_ERROR.value(),
          "Unexpected error updating SSO data. 200 OK response content: " + updateSSOResponse);
    }
    log.debug("Update SSO request success for user: {}", userId);
  }
}
