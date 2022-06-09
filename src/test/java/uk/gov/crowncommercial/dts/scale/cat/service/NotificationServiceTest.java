package uk.gov.crowncommercial.dts.scale.cat.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import uk.gov.service.notify.NotificationClient;
import uk.gov.service.notify.SendEmailResponse;

/**
 *
 */
@SpringBootTest(classes = {NotificationService.class}, webEnvironment = WebEnvironment.NONE)
class NotificationServiceTest {

  static final String TEMPLATE_ID = "abcdef-123456";
  static final String TARGET_EMAIL = "john.smith@example.com";
  static final String REFERENCE = "REF123";
  static final Map<String, String> PLACEHOLDERS = Map.of("name", "john smith");

  @MockBean
  private NotificationClient client;

  @Autowired
  private NotificationService notificationService;

  @Test
  void testSendMailSuccess() throws Exception {

    var sendEmailResponse = new SendEmailResponse("Success");

    when(client.sendEmail(TEMPLATE_ID, TARGET_EMAIL, PLACEHOLDERS, REFERENCE))
        .thenReturn(sendEmailResponse);

    notificationService.sendEmail(TEMPLATE_ID, TARGET_EMAIL, PLACEHOLDERS, REFERENCE);

    verify(client.sendEmail(TEMPLATE_ID, TARGET_EMAIL, PLACEHOLDERS, REFERENCE));
  }

}
