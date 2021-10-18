package uk.gov.crowncommercial.dts.scale.cat.service;

import static java.util.Optional.ofNullable;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import uk.gov.crowncommercial.dts.scale.cat.exception.AgreementsServiceApplicationException;
import uk.gov.crowncommercial.dts.scale.cat.exception.ResourceNotFoundException;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.DataTemplate;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.RequirementGroup;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.TemplateCriteria;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.*;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.QuestionNonOCDS.QuestionTypeEnum;

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
    var event = validationService.validateProjectAndEventIds(projectId, eventId);

    // Call the AS to get the template criteria
    var lotEventTypeDataTemplates =
        agreementsService.getLotEventTypeDataTemplates(event.getProject().getCaNumber(),
            event.getProject().getLotNumber(), EventType.fromValue(event.getEventType()));

    // TODO: Decide how to handle multiple data templates being returned by AS
    var dataTemplate = lotEventTypeDataTemplates.stream().findFirst()
        .orElseThrow(() -> new AgreementsServiceApplicationException("Data template not found"));

    // Convert to EvalCriteria and return
    return dataTemplate
        .getCriteria().stream().map(tc -> new EvalCriteria().id(tc.getId())
            .description(tc.getDescription()).description(tc.getDescription()).title(tc.getTitle()))
        .collect(Collectors.toSet());
  }

  public Set<QuestionGroup> getEvalCriterionGroups(final Integer projectId, final String eventId,
      final String criterionId) {
    var event = validationService.validateProjectAndEventIds(projectId, eventId);

    var lotEventTypeDataTemplates =
        agreementsService.getLotEventTypeDataTemplates(event.getProject().getCaNumber(),
            event.getProject().getLotNumber(), EventType.fromValue(event.getEventType()));

    // TODO: Decide how to handle multiple data templates being returned by AS
    var dataTemplate = lotEventTypeDataTemplates.stream().findFirst()
        .orElseThrow(() -> new AgreementsServiceApplicationException("Data template not found"));

    var criteria = extractTemplateCriteria(dataTemplate, criterionId);

    return criteria.getRequirementGroups().stream().map(rg -> {
      var questionGroupNonOCDS = new QuestionGroupNonOCDS().task(rg.getNonOCDS().getTask())
          .prompt(rg.getNonOCDS().getPrompt()).mandatory(rg.getNonOCDS().getMandatory());
      var questionGroupOCDS = new RequirementGroup1().id(rg.getOcds().getId())
          .description(rg.getOcds().getDescription());
      return new QuestionGroup().nonOCDS(questionGroupNonOCDS).OCDS(questionGroupOCDS);
    }).collect(Collectors.toSet());
  }

  public Set<Question> getEvalCriterionGroupQuestions(final Integer projectId, final String eventId,
      final String criterionId, final String groupId) {
    var event = validationService.validateProjectAndEventIds(projectId, eventId);

    var lotEventTypeDataTemplates =
        agreementsService.getLotEventTypeDataTemplates(event.getProject().getCaNumber(),
            event.getProject().getLotNumber(), EventType.fromValue(event.getEventType()));

    // TODO: Decide how to handle multiple data templates being returned by AS
    var dataTemplate = lotEventTypeDataTemplates.stream().findFirst()
        .orElseThrow(() -> new AgreementsServiceApplicationException("Data template not found"));

    var criteria = extractTemplateCriteria(dataTemplate, criterionId);
    var group = extractRequirementGroup(criteria, groupId);

    return group.getOcds().getRequirements().stream().map(r -> {
      // TODO: Move to object mapper or similar
      // @formatter:off
      var questionNonOCDS = new QuestionNonOCDS()
          .questionType(QuestionTypeEnum.fromValue(r.getNonOCDS().getQuestionType()))
          .mandatory(r.getNonOCDS().getMandatory())
          .multiAnswer(r.getNonOCDS().getMultiAnswer())
          .answered(r.getNonOCDS().getAnswered())
          .options(ofNullable(r.getNonOCDS().getOptions()).orElseGet(List::of).stream().map(o ->
              new QuestionNonOCDSOptions().value(o.get("value"))
                       .selected(Boolean.valueOf(o.get("selected")))).collect(Collectors.toList()));

      var questionOCDS = new Requirement1()
          .id(r.getOcds().getId())
          .title(r.getOcds().getTitle())
          .description(r.getOcds().getDescription())
          .dataType(DataType.fromValue(r.getOcds().getDataType()))
          .pattern(r.getOcds().getPattern())
          .expectedValue(new Value1().amount(r.getOcds().getExpectedValue()))
          .minValue(new Value1().amount(r.getOcds().getMinValue()))
          .maxValue(new Value1().amount(r.getOcds().getMaxValue()))
          .period(r.getOcds().getPeriod() != null ? new Period1()
              .startDate(r.getOcds().getPeriod().getStartDate())
              .endDate(r.getOcds().getPeriod().getEndDate())
              .maxExtentDate(r.getOcds().getPeriod().getMaxExtentDate())
              .durationInDays(r.getOcds().getPeriod().getDurationInDays()) : null);
      // @formatter:on
      return new Question().nonOCDS(questionNonOCDS).OCDS(questionOCDS);
    }).collect(Collectors.toSet());

  }

  private TemplateCriteria extractTemplateCriteria(final DataTemplate dataTemplate,
      final String criterionId) {
    return dataTemplate.getCriteria().stream().filter(tc -> Objects.equals(tc.getId(), criterionId))
        .findFirst().orElseThrow(
            () -> new ResourceNotFoundException("Criterion '" + criterionId + "' not found"));
  }

  private RequirementGroup extractRequirementGroup(final TemplateCriteria criteria,
      final String groupId) {
    return criteria.getRequirementGroups().stream()
        .filter(rg -> Objects.equals(rg.getOcds().getId(), groupId)).findFirst().orElseThrow(
            () -> new ResourceNotFoundException("Criterion group '" + groupId + "' not found"));

  }

}
