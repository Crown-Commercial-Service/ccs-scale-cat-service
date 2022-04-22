package uk.gov.crowncommercial.dts.scale.cat.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.exception.AuthorisationFailureException;
import uk.gov.crowncommercial.dts.scale.cat.exception.ResourceNotFoundException;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.QuestionAndAnswer;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.Timestamps;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.QandA;
import uk.gov.crowncommercial.dts.scale.cat.repo.QuestionAndAnswerRepo;

/**
 *
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QuestionAndAnswerService {

  private final ValidationService validationService;
  private final UserProfileService userService;
  private final QuestionAndAnswerRepo questionAndAnswerRepo;
  public static final String JAGGAER_USER_NOT_FOUND = "Jaggaer user not found";
  public static final String Q_AND_A_NOT_FOUND = "QuestionAndAnswer not found by this id %s";

  public QandA createOrUpdateQuestionAndAnswer(final String profile, final Integer projectId,
      final String eventId, final QandA qAndA, final Integer qaId) {
    var procurementEvent = validationService.validateProjectAndEventIds(projectId, eventId);
    var user = userService.resolveBuyerUserByEmail(profile)
        .orElseThrow(() -> new AuthorisationFailureException(JAGGAER_USER_NOT_FOUND));

    var questionAndAnswer = QuestionAndAnswer.builder().question(qAndA.getQuestion())
        .answer(qAndA.getAnswer()).timestamps(Timestamps.createTimestamps(user.getEmail()))
        .event(procurementEvent).build();

    if (qaId != null) {
      log.info("Updating Q&A to {}", qaId);
      var exQuestionAndAnswer =
          questionAndAnswerRepo.findByIdAndEventId(qaId, procurementEvent.getId()).orElseThrow(
              () -> new ResourceNotFoundException(String.format(Q_AND_A_NOT_FOUND, qaId)));

      questionAndAnswer = exQuestionAndAnswer.setQuestion(qAndA.getQuestion())
          .setAnswer(qAndA.getAnswer()).setTimestamps(
              Timestamps.updateTimestamps(exQuestionAndAnswer.getTimestamps(), user.getEmail()));
    }
    return convertQandA(questionAndAnswerRepo.save(questionAndAnswer));
  }

  public List<QandA> getQuestionAndAnswerByEvent(final Integer projectId, final String eventId) {
    var procurementEvent = validationService.validateProjectAndEventIds(projectId, eventId);
    var qandAnswers = questionAndAnswerRepo.findByEventId(procurementEvent.getId());
    return covertQandAList(qandAnswers);
  }

  private QandA convertQandA(QuestionAndAnswer questionAndAnswer) {
    return new QandA().id(BigDecimal.valueOf(questionAndAnswer.getId()))
        .question(questionAndAnswer.getQuestion()).answer(questionAndAnswer.getAnswer())
        .lastUpdated(questionAndAnswer.getTimestamps().getUpdatedAt() == null ? null
            : OffsetDateTime.ofInstant(questionAndAnswer.getTimestamps().getUpdatedAt(),
                ZoneId.systemDefault()))
        .created(OffsetDateTime.ofInstant(questionAndAnswer.getTimestamps().getCreatedAt(),
            ZoneId.systemDefault()));
  }

  private List<QandA> covertQandAList(Set<QuestionAndAnswer> questionAndAnswerList) {
    return questionAndAnswerList.stream().map(this::convertQandA).collect(Collectors.toList());
  }

}
