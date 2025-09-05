package uk.gov.crowncommercial.dts.scale.cat.service;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.retry.ExhaustedRetryException;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gov.crowncommercial.dts.scale.cat.exception.NotificationApplicationException;
import uk.gov.service.notify.NotificationClient;
import uk.gov.service.notify.NotificationClientException;
import uk.gov.service.notify.SendEmailResponse;

/**
 *
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

  static final String TEMPLATE_ID = "abcdef-123456";
  static final String TARGET_EMAIL = "john.smith@example.com";
  static final String REFERENCE = "REF123";
  static final Map<String, String> PLACEHOLDERS = Map.of("name", "john smith");

  @Mock
  private NotificationClient client;

  @Mock
  private SendEmailResponse emailResponse;

  @InjectMocks
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
    var notificationClientException = new NotificationClientException("bang");
    ReflectionTestUtils.setField(notificationClientException, "httpResult", 500);

    when(client.sendEmail(TEMPLATE_ID, TARGET_EMAIL, PLACEHOLDERS, REFERENCE))
            .thenThrow(notificationClientException);

    var ex = assertThrows(NotificationApplicationException.class,
            () -> notificationService.sendEmail(TEMPLATE_ID, TARGET_EMAIL, PLACEHOLDERS, REFERENCE));

    assertTrue(ex.getMessage().contains("bang"));
    verify(client, times(1)).sendEmail(TEMPLATE_ID, TARGET_EMAIL, PLACEHOLDERS, REFERENCE);
  }

  @Test
  void testSendEmailFailRetriesExhausted() throws Exception {
    var notificationClientException = new NotificationClientException("bang");
    ReflectionTestUtils.setField(notificationClientException, "httpResult", 500);

    when(client.sendEmail(TEMPLATE_ID, TARGET_EMAIL, PLACEHOLDERS, REFERENCE))
            .thenThrow(notificationClientException);

    var ex = assertThrows(NotificationApplicationException.class,
            () -> notificationService.sendEmail(TEMPLATE_ID, TARGET_EMAIL, PLACEHOLDERS, REFERENCE));

    assertTrue(ex.getMessage().contains("bang"));
    verify(client, times(1)).sendEmail(TEMPLATE_ID, TARGET_EMAIL, PLACEHOLDERS, REFERENCE);
  }
}
