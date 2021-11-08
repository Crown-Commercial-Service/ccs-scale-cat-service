package uk.gov.crowncommercial.dts.scale.cat.service;

import static java.time.Duration.ofSeconds;
import static java.util.Optional.ofNullable;
import static uk.gov.crowncommercial.dts.scale.cat.config.AgreementsServiceAPIConfig.KEY_URI_TEMPLATE;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import lombok.RequiredArgsConstructor;
import uk.gov.crowncommercial.dts.scale.cat.config.ConclaveAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.exception.AgreementsServiceApplicationException;
import uk.gov.crowncommercial.dts.scale.cat.model.conclave.ConclaveUser;

/**
 *
 */
@Service
@RequiredArgsConstructor
public class ConclaveService {

  private final WebClient conclaveWebClient;
  private final ConclaveAPIConfig conclaveAPIConfig;

  public ConclaveUser getUser(final String userId) {

    var getUserTemplateURI = conclaveAPIConfig.getGetUser().get(KEY_URI_TEMPLATE);

    return ofNullable(conclaveWebClient.get().uri(getUserTemplateURI, userId).retrieve()
        .bodyToMono(ConclaveUser.class).block(ofSeconds(conclaveAPIConfig.getTimeoutDuration())))
            .orElseThrow(() -> new AgreementsServiceApplicationException(
                "Unexpected error retrieving RFI template from AS"));
  }

}
