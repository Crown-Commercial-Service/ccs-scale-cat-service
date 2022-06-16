package uk.gov.crowncommercial.dts.scale.cat.service;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.retry.ExhaustedRetryException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gov.crowncommercial.dts.scale.cat.config.RetryConfig;
import uk.gov.crowncommercial.dts.scale.cat.exception.NotificationApplicationException;
import uk.gov.service.notify.NotificationClient;
import uk.gov.service.notify.NotificationClientException;
import uk.gov.service.notify.SendEmailResponse;

/**
 *
 */
@SpringBootTest(classes = {NotificationService.class, RetryConfig.class},
    webEnvironment = WebEnvironment.NONE)
@ActiveProfiles("test")
class NotificationServiceTest {

  static final String TEMPLATE_ID = "abcdef-123456";
  static final String TARGET_EMAIL = "john.smith@example.com";
  static final String REFERENCE = "REF123";
  static final Map<String, String> PLACEHOLDERS = Map.of("name", "john smith");

  @MockBean
  private NotificationClient client;

  @MockBean
  private SendEmailResponse emailResponse;

  @Autowired
  private NotificationService notificationService;

  @Test
  void testSendEmailSuccess() throws Exception {

    when(client.sendEmail(TEMPLATE_ID, TARGET_EMAIL, PLACEHOLDERS, REFERENCE))
        .thenReturn(emailResponse);

    notificationService.sendEmail(TEMPLATE_ID, TARGET_EMAIL, PLACEHOLDERS, REFERENCE);

    verify(client).sendEmail(TEMPLATE_ID, TARGET_EMAIL, PLACEHOLDERS, REFERENCE);
  }

  @Test
  void testSendEmailFailNoRetry() throws Exception {

    // HTTP 400 - no point retrying
    var notificationClientException = new NotificationClientException("bang");

    when(client.sendEmail(TEMPLATE_ID, TARGET_EMAIL, PLACEHOLDERS, REFERENCE))
        .thenThrow(notificationClientException);

    notificationService.sendEmail(TEMPLATE_ID, TARGET_EMAIL, PLACEHOLDERS, REFERENCE);

    // Once and only once
    verify(client).sendEmail(TEMPLATE_ID, TARGET_EMAIL, PLACEHOLDERS, REFERENCE);
  }

  @Test
  void testSendEmailFailRetrySuccess() throws Exception {
    // HTTP 500 - retry
    var notificationClientException = new NotificationClientException("bang");
    ReflectionTestUtils.setField(notificationClientException, "httpResult", 500);

    when(client.sendEmail(TEMPLATE_ID, TARGET_EMAIL, PLACEHOLDERS, REFERENCE))
        .thenThrow(notificationClientException).thenReturn(emailResponse);

    notificationService.sendEmail(TEMPLATE_ID, TARGET_EMAIL, PLACEHOLDERS, REFERENCE);

    verify(client, times(2)).sendEmail(TEMPLATE_ID, TARGET_EMAIL, PLACEHOLDERS, REFERENCE);
  }

  @Test
  void testSendEmailFailRetriesExhausted() throws Exception {
    // HTTP 500 - retry
    var notificationClientException = new NotificationClientException("bang");
    ReflectionTestUtils.setField(notificationClientException, "httpResult", 500);

    when(client.sendEmail(TEMPLATE_ID, TARGET_EMAIL, PLACEHOLDERS, REFERENCE)).thenThrow(
        notificationClientException, notificationClientException, notificationClientException);

    var exhaustedRetryEx = assertThrows(ExhaustedRetryException.class,
        () -> notificationService.sendEmail(TEMPLATE_ID, TARGET_EMAIL, PLACEHOLDERS, REFERENCE));

    assertTrue(exhaustedRetryEx.getMessage().startsWith("Retries exhausted"));

    assertSame(NotificationApplicationException.class, exhaustedRetryEx.getCause().getClass());
    verify(client, times(3)).sendEmail(TEMPLATE_ID, TARGET_EMAIL, PLACEHOLDERS, REFERENCE);
  }

}
