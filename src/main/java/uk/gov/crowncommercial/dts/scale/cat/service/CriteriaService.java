package uk.gov.crowncommercial.dts.scale.cat.service;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import uk.gov.crowncommercial.dts.scale.cat.exception.AgreementsServiceApplicationException;
import uk.gov.crowncommercial.dts.scale.cat.exception.ResourceNotFoundException;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.*;

/**
 *
 */
@Service
@RequiredArgsConstructor
public class CriteriaService {

  private final AgreementsService agreementsService;
  private final ValidationService validationService;

  public Set<EvalCriteria> getEvalCriteria(final Integer projectId, final String eventId) {

    // Get project from tenders DB to obtain Jaggaer project id
    final var event = validationService.validateProjectAndEventIds(projectId, eventId);

    // Call the AS to get the template criteria
    final var lotEventTypeDataTemplates =
        agreementsService.getLotEventTypeDataTemplates(event.getProject().getCaNumber(),
            event.getProject().getLotNumber(), EventType.fromValue(event.getEventType()));

    // TODO: Decide how to handle multiple data templates being returned by AS
    final var dataTemplate = lotEventTypeDataTemplates.stream().findFirst()
        .orElseThrow(() -> new AgreementsServiceApplicationException("Data template not found"));

    // Convert to EvalCriteria and return
    return dataTemplate
        .getCriteria().stream().map(tc -> new EvalCriteria().id(tc.getId())
            .description(tc.getDescription()).description(tc.getDescription()).title(tc.getTitle()))
        .collect(Collectors.toSet());
  }

  public Set<QuestionGroup> getEvalCriterionGroups(final Integer projectId, final String eventId,
      final String criterionId) {
    final var event = validationService.validateProjectAndEventIds(projectId, eventId);

    final var lotEventTypeDataTemplates =
        agreementsService.getLotEventTypeDataTemplates(event.getProject().getCaNumber(),
            event.getProject().getLotNumber(), EventType.fromValue(event.getEventType()));

    // TODO: Decide how to handle multiple data templates being returned by AS
    final var dataTemplate = lotEventTypeDataTemplates.stream().findFirst()
        .orElseThrow(() -> new AgreementsServiceApplicationException("Data template not found"));

    final var criteria = dataTemplate.getCriteria().stream()
        .filter(tc -> Objects.equals(tc.getId(), criterionId)).findFirst().orElseThrow(
            () -> new ResourceNotFoundException("Criterion '" + criterionId + "' not found"));

    return criteria.getRequirementGroups().stream().map(rg -> {
      final var questionGroupNonOCDS = new QuestionGroupNonOCDS().task(rg.getNonOCDS().getTask())
          .prompt(rg.getNonOCDS().getPrompt()).mandatory(rg.getNonOCDS().getMandatory());
      final var questionGroupOCDS = new RequirementGroup1().id(rg.getOcds().getId())
          .description(rg.getOcds().getDescription());
      return new QuestionGroup().nonOCDS(questionGroupNonOCDS).OCDS(questionGroupOCDS);
    }).collect(Collectors.toSet());
  }

}
