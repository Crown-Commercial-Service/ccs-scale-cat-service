package uk.gov.crowncommercial.dts.scale.cat.service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import uk.gov.crowncommercial.dts.scale.cat.clients.AgreementsClient;
import uk.gov.crowncommercial.dts.scale.cat.clients.QuestionAndAnswerClient;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.DataTemplate;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.TemplateCriteria;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.ViewEventType;

/**
 * Agreements Service layer tests
 */
@ExtendWith(MockitoExtension.class)
class AgreementsServiceTest {

  private static final Integer TEMPLATE_ID = 1;
  private static final String TEMPLATE_NAME = "template1";
  private static final Integer TEMPLATE_PARENT = 0;
  private static final Boolean TEMPLATE_MANDATORY = Boolean.FALSE;
  private static final List<TemplateCriteria> TEMPLATE_CRITERIA = List.of();

  private static final String AGREEMENT_ID = "agreement1";
  private static final String LOT_ID = "lot1";
  private static final ViewEventType EVENT_TYPE = ViewEventType.FC;

  private static final String API_KEY = "apiKey";

  @InjectMocks
  private AgreementsService agreementsService;

  @Mock
  private AgreementsClient agreementsClient;

  @Mock
  private QuestionAndAnswerClient questionAndAnswerClient;

  @BeforeEach
  void setup() {
    agreementsService.serviceApiKey = API_KEY;
    agreementsService.questionAndAnswerServiceApiKey = API_KEY;
  }

  @Test
  void shouldReturnTemplateFromQuestionAndAnswerService() throws Exception {
    // Mock behaviours
    when(questionAndAnswerClient.getEventDataTemplates(eq(AGREEMENT_ID), eq(LOT_ID), eq(EVENT_TYPE.getValue()), eq(API_KEY)))
        .thenReturn(List.of(
            DataTemplate.builder()
                .id(TEMPLATE_ID)
                .templateName(TEMPLATE_NAME)
                .parent(TEMPLATE_PARENT)
                .mandatory(TEMPLATE_MANDATORY)
                .criteria(TEMPLATE_CRITERIA)
            .build()
        )
    );

    // Invoke
    var response = agreementsService.getLotEventTypeDataTemplates(AGREEMENT_ID, LOT_ID, EVENT_TYPE);

    // Assert
    assertAll(() -> assertNotNull(response),
              () -> assertEquals(1, response.size()),
              () -> assertEquals(TEMPLATE_NAME, response.get(0).getTemplateName()));

    // Verify
    verify(questionAndAnswerClient).getEventDataTemplates(eq(AGREEMENT_ID), eq(LOT_ID), eq(EVENT_TYPE.getValue()), eq(API_KEY));
    verifyNoInteractions(agreementsClient);
  }

  @Test
  void shouldReturnEmptyListWhenDos7EnabledAndQuestionAndAnswerServiceHasNoMatch() throws Exception {
    // Mock behaviours
    agreementsService.dos7Enabled = true;

    when(questionAndAnswerClient.getEventDataTemplates(eq(AGREEMENT_ID), eq(LOT_ID), eq(EVENT_TYPE.getValue()), eq(API_KEY)))
        .thenReturn(null);

    // Invoke
    var response = agreementsService.getLotEventTypeDataTemplates(AGREEMENT_ID, LOT_ID, EVENT_TYPE);

    // Assert
    assertAll(() -> assertNotNull(response),
              () -> assertEquals(0, response.size()));

    // Verify
    verify(questionAndAnswerClient).getEventDataTemplates(eq(AGREEMENT_ID), eq(LOT_ID), eq(EVENT_TYPE.getValue()), eq(API_KEY));
    verifyNoInteractions(agreementsClient);
  }

  @Test
  void shouldReturnTemplateFromAgreementServiceWhenDos7DisabledAndQuestionAndAnswerServiceHasNoMatch() throws Exception {
    // Mock behaviours
    agreementsService.dos7Enabled = false;

    when(questionAndAnswerClient.getEventDataTemplates(eq(AGREEMENT_ID), eq(LOT_ID), eq(EVENT_TYPE.getValue()), eq(API_KEY)))
        .thenReturn(null);

    when(agreementsClient.getEventDataTemplates(eq(AGREEMENT_ID), eq(LOT_ID), eq(EVENT_TYPE.getValue()), eq(API_KEY)))
        .thenReturn(List.of(
            DataTemplate.builder()
                .id(TEMPLATE_ID)
                .templateName(TEMPLATE_NAME)
                .parent(TEMPLATE_PARENT)
                .mandatory(TEMPLATE_MANDATORY)
                .criteria(TEMPLATE_CRITERIA)
            .build()
        )
    );

    // Invoke
    var response = agreementsService.getLotEventTypeDataTemplates(AGREEMENT_ID, LOT_ID, EVENT_TYPE);

    // Assert
    assertAll(() -> assertNotNull(response),
              () -> assertEquals(1, response.size()),
              () -> assertEquals(TEMPLATE_NAME, response.get(0).getTemplateName()));

    // Verify
    verify(questionAndAnswerClient).getEventDataTemplates(eq(AGREEMENT_ID), eq(LOT_ID), eq(EVENT_TYPE.getValue()), eq(API_KEY));
    verify(agreementsClient).getEventDataTemplates(eq(AGREEMENT_ID), eq(LOT_ID), eq(EVENT_TYPE.getValue()), eq(API_KEY));
  }
}
