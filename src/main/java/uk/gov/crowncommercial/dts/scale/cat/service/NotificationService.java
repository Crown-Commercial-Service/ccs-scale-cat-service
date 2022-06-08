package uk.gov.crowncommercial.dts.scale.cat.service;

import java.util.Map;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.config.TendersRetryable;
import uk.gov.crowncommercial.dts.scale.cat.exception.NotificationApplicationException;
import uk.gov.service.notify.NotificationClient;
import uk.gov.service.notify.NotificationClientException;

/**
 * Wrapper around GOV.UK Notify API client
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

  private final NotificationClient client;

  @TendersRetryable
  public void sendEmail(final String targetEmail, final String templateId,
      final Map<String, ?> placeholders, final String reference) {

    try {
      log.info("Sending email via GOV.UK Notify to [{}] using template [{}]", targetEmail,
          templateId);
      var response = client.sendEmail(templateId, targetEmail, placeholders, reference);

      log.debug("Send email response from GOV.UK Notify: [{}]", response);
    } catch (NotificationClientException nce) {

      if (nce.getHttpResult() == 500) {
        log.error("Internal Server Error from GOV.UK Notify, retying..", nce);
        throw new NotificationApplicationException(nce.getHttpResult(), nce.getMessage());
      }
      // Log the error - nothing else to do.
      log.error("Unrecoverable error from GOV.UK Notify", nce);

    }

  }

}
