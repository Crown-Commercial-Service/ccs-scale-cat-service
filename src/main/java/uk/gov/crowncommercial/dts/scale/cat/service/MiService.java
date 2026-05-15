package uk.gov.crowncommercial.dts.scale.cat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.crowncommercial.dts.scale.cat.exception.ResourceNotFoundException;
import uk.gov.crowncommercial.dts.scale.cat.model.assessment.MiQuestionAnswer;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.MiQuestionAnswerEntity;
import uk.gov.crowncommercial.dts.scale.cat.repo.MiQuestionAnswerRepo;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;

import java.util.List;

import static java.lang.String.format;

/**
 * Mi service to add e-procurement MI details into the tender db.
 */

@Service
@RequiredArgsConstructor
@Slf4j
public class MiService {

    private static final String ERR_MSG_FMT_CONCLAVE_USER_MISSING = "User [%s] not found in Conclave";
    private static final String ERR_MSG_FMT_E_PROCUREMENT_DETAILS_MISSING = "No e-procurement details found for [%s]";

    private final ConclaveService conclaveService;
    private final RetryableTendersDBDelegate retryableTendersDBDelegate;
    private final MiQuestionAnswerRepo miQuestionAnswerRepo;

    @Transactional
    public Integer createMiQuestionAndAnswer(final List<MiQuestionAnswer> miQuestionAnswers, final String principal) {

        log.debug("Adding e-procurement MI question and answer for principal: {}", principal);

        var conclaveUser = conclaveService.getUserProfile(principal).orElseThrow(
                () -> new ResourceNotFoundException(format(ERR_MSG_FMT_CONCLAVE_USER_MISSING, principal)));


        // Map DTOs to Entities correctly using Streams
        List<MiQuestionAnswerEntity> entitiesToSave = miQuestionAnswers.stream()
                .map(dto -> mapMiQuestionAnswerToEntity(dto, principal))
                .toList();

        // Save all entities
        List<MiQuestionAnswerEntity> savedEntities = retryableTendersDBDelegate.saveAllQuestionsAndAnswers(entitiesToSave);

        if (!savedEntities.isEmpty()) {
            log.debug("Successfully saved {} e-procurement MI questions and answers.", savedEntities.size());
        }

        return savedEntities.size();
    }

    private MiQuestionAnswerEntity mapMiQuestionAnswerToEntity(MiQuestionAnswer miQuestionAnswer, final String principal) {
        MiQuestionAnswerEntity entity = new MiQuestionAnswerEntity();
        entity.setAssessmentId(miQuestionAnswer.getAssessmentId());
        entity.setProjectId(miQuestionAnswer.getProjectId());
        entity.setEventId(miQuestionAnswer.getEventId());
        entity.setQuestionId(miQuestionAnswer.getQuestionId());
        entity.setAnswer(miQuestionAnswer.getQuestionAnswer());
        entity.setCreatedBy(principal);
        return entity;
    }

    @Transactional
    public List<MiQuestionAnswer> getMiDetails(final String principal) {

        List<MiQuestionAnswerEntity> entities = retryableTendersDBDelegate.findByCreatedBy(principal);

        if (entities == null || entities.isEmpty()) {
            throw new ResourceNotFoundException(format(ERR_MSG_FMT_E_PROCUREMENT_DETAILS_MISSING, principal));
        }

        // Map the List of Entities to a List of DTOs
        return entities.stream()
                .map(entity -> MiQuestionAnswer.builder()
                        .assessmentId(entity.getAssessmentId())
                        .projectId(entity.getProjectId())
                        .eventId(entity.getEventId())
                        .questionId(entity.getQuestionId())
                        .questionAnswer(entity.getAnswer())
                        .createdBy(entity.getCreatedBy())
                        .createdAt(entity.getCreatedAt())
                        .build())
                .toList();
    }

  @Transactional(readOnly = true)
  public List<MiQuestionAnswerEntity> findAllByProjectId(final String projectId) {
    return miQuestionAnswerRepo.findAllByProjectIdIgnoreCase(projectId);
  }
}
