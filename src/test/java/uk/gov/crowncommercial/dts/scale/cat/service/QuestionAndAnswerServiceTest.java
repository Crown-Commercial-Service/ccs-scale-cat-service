package uk.gov.crowncommercial.dts.scale.cat.service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.QuestionAndAnswer;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.Timestamps;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.QandA;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.SubUsers.SubUser;
import uk.gov.crowncommercial.dts.scale.cat.repo.QuestionAndAnswerRepo;

/**
 * QuestionAndAnswerService Service layer tests
 */
@SpringBootTest(classes = {QuestionAndAnswerService.class}, webEnvironment = WebEnvironment.NONE)
class QuestionAndAnswerServiceTest {

  private static final String PRINCIPAL = "venki@bric.org.uk";
  private static final Integer QUESTION_ID = 1;
  private static final String QUESTION = "My Question1";
  private static final String ANSWER = "My Answer1";
  private static final String BUYER_USER_NAME = "Venki Bathula";
  private static final String EVENT_OCID = "ocds-abc123-1";
  private static final Integer PROC_PROJECT_ID = 1;
  private static final String JAGGAER_USER_ID = "12345";
  private static final Integer EVENT_ID = 2;
  private static final Optional<SubUser> JAGGAER_USER = Optional
      .of(SubUser.builder().userId(JAGGAER_USER_ID).email(PRINCIPAL).name(BUYER_USER_NAME).build());
  private static final Instant CREATED_DATE = Instant.parse("2022-04-08T13:42:01.895Z");
  private static final Instant LAST_UPDATED = Instant.parse("2022-04-08T13:42:01.895Z");

  @MockBean
  private UserProfileService userProfileService;

  @MockBean
  private ValidationService validationService;

  @Autowired
  private QuestionAndAnswerService questionAndAnswerService;

  @MockBean
  private QuestionAndAnswerRepo questionAndAnswerRepo;

  @Test
  void testCreateQuestionAndAnswer() throws Exception {
    // Stub some objects
    var qAndA = new QandA().question(QUESTION).answer(ANSWER);

    // Mock behaviours
    when(userProfileService.resolveBuyerUserByEmail(PRINCIPAL)).thenReturn(JAGGAER_USER);
    when(validationService.validateProjectAndEventIds(PROC_PROJECT_ID, EVENT_OCID))
        .thenReturn(ProcurementEvent.builder().id(EVENT_ID).build());

    var questionAndAnswer = new QuestionAndAnswer();

    when(questionAndAnswerRepo.save(any(QuestionAndAnswer.class))).then(mock -> {
      questionAndAnswer.setId(1);
      questionAndAnswer.setQuestion(QUESTION);
      questionAndAnswer.setAnswer(ANSWER);
      questionAndAnswer.setTimestamps(Timestamps.createTimestamps(PRINCIPAL));
      return questionAndAnswer;
    });

    // Invoke
    var response = questionAndAnswerService.createOrUpdateQuestionAndAnswer(PRINCIPAL,
        PROC_PROJECT_ID, EVENT_OCID, qAndA, null);

    // Assert
    assertAll(() -> assertNotNull(response), () -> assertEquals(QUESTION, response.getQuestion()));

  }

  @Test
  void testUpdateQuestionAndAnswer() throws Exception {
    // Stub some objects
    var qAndA = new QandA().question(QUESTION).answer(ANSWER);

    // Mock behaviours
    when(userProfileService.resolveBuyerUserByEmail(PRINCIPAL)).thenReturn(JAGGAER_USER);
    when(validationService.validateProjectAndEventIds(PROC_PROJECT_ID, EVENT_OCID))
        .thenReturn(ProcurementEvent.builder().id(EVENT_ID).build());

    var questionAndAnswer = new QuestionAndAnswer();
    var ctime = new Timestamps();
    ctime.setCreatedAt(CREATED_DATE);
    ctime.setCreatedBy(PRINCIPAL);
    ctime.setUpdatedAt(LAST_UPDATED);
    ctime.setUpdatedBy(PRINCIPAL);

    when(questionAndAnswerRepo.findByIdAndEventId(QUESTION_ID, EVENT_ID)).then(mock -> {
      questionAndAnswer.setId(1).setTimestamps(ctime);
      return Optional.of(questionAndAnswer);
    });

    when(questionAndAnswerRepo.save(any(QuestionAndAnswer.class))).then(mock -> {
      questionAndAnswer.setId(1);
      questionAndAnswer.setQuestion(QUESTION);
      questionAndAnswer.setAnswer(ANSWER);
      questionAndAnswer.setTimestamps(ctime);
      return questionAndAnswer;
    });

    // Invoke
    var response = questionAndAnswerService.createOrUpdateQuestionAndAnswer(PRINCIPAL,
        PROC_PROJECT_ID, EVENT_OCID, qAndA, QUESTION_ID);

    // Assert
    assertAll(() -> assertNotNull(response), () -> assertEquals(QUESTION, response.getQuestion()),
        () -> assertEquals(BigDecimal.ONE, response.getId()),
        () -> assertEquals(OffsetDateTime.ofInstant(CREATED_DATE, ZoneId.systemDefault()),
            response.getCreated()));
  }

  @Test
  void testGetAllQuestionAndAnswers() throws Exception {
    // Stub some objects
    Timestamps updateTimestamps =
        Timestamps.updateTimestamps(Timestamps.createTimestamps(PRINCIPAL), PRINCIPAL);
    var questions = List.of(
        QuestionAndAnswer.builder().id(1).question(QUESTION).timestamps(updateTimestamps).build(),
        QuestionAndAnswer.builder().id(2).question(QUESTION).timestamps(updateTimestamps).build());
    Set<QuestionAndAnswer> targetSet = new HashSet<>(questions);

    // Mock behaviours
    when(userProfileService.resolveBuyerUserByEmail(PRINCIPAL)).thenReturn(JAGGAER_USER);
    when(validationService.validateProjectAndEventIds(PROC_PROJECT_ID, EVENT_OCID))
        .thenReturn(ProcurementEvent.builder().id(EVENT_ID).build());
    when(questionAndAnswerRepo.findByEventId(EVENT_ID)).then(mock -> {
      return targetSet;
    });

    // Invoke
    var response =
        questionAndAnswerService.getQuestionAndAnswerByEvent(PROC_PROJECT_ID, EVENT_OCID);

    // Assert
    assertAll(() -> assertNotNull(response));
  }

}
