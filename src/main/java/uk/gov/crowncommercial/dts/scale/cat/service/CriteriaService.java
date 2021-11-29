package uk.gov.crowncommercial.dts.scale.cat.service;

import static java.time.Duration.ofSeconds;
import static java.util.Optional.ofNullable;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig.ENDPOINT;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.config.Constants;
import uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.exception.AgreementsServiceApplicationException;
import uk.gov.crowncommercial.dts.scale.cat.exception.JaggaerApplicationException;
import uk.gov.crowncommercial.dts.scale.cat.exception.ResourceNotFoundException;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.DataTemplate;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.Party;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.Requirement;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.Requirement.Option;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.RequirementGroup;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.TemplateCriteria;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.*;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.QuestionNonOCDS.QuestionTypeEnum;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.*;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;

/**
 *
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CriteriaService {

  static final String ERR_MSG_DATA_TEMPLATE_NOT_FOUND = "Data template not found";

  private final AgreementsService agreementsService;
  private final ValidationService validationService;
  private final RetryableTendersDBDelegate retryableTendersDBDelegate;
  private final JaggaerAPIConfig jaggaerAPIConfig;
  private final WebClient jaggaerWebClient;

  public Set<EvalCriteria> getEvalCriteria(final Integer projectId, final String eventId) {

    // Get project from tenders DB to obtain Jaggaer project id
    var event = validationService.validateProjectAndEventIds(projectId, eventId);
    var dataTemplate = retrieveDataTemplate(event);

    // Convert to EvalCriteria and return
    return dataTemplate
        .getCriteria().stream().map(tc -> new EvalCriteria().id(tc.getId())
            .description(tc.getDescription()).description(tc.getDescription()).title(tc.getTitle()))
        .collect(Collectors.toSet());
  }

  public Set<QuestionGroup> getEvalCriterionGroups(final Integer projectId, final String eventId,
      final String criterionId) {
    var event = validationService.validateProjectAndEventIds(projectId, eventId);
    var dataTemplate = retrieveDataTemplate(event);
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
    var dataTemplate = retrieveDataTemplate(event);
    var criteria = extractTemplateCriteria(dataTemplate, criterionId);
    var group = extractRequirementGroup(criteria, groupId);

    return group.getOcds().getRequirements().stream().map(this::convertRequirementToQuestion)
        .collect(Collectors.toSet());

  }

  public Question putQuestionOptionDetails(final Question question, final Integer projectId,
      final String eventId, final String criterionId, final String groupId,
      final String questionId) {

    // Get the project/event and check if there is a pre-existing event.procurement_template_payload
    var event = validationService.validateProjectAndEventIds(projectId, eventId);
    var dataTemplate = retrieveDataTemplate(event);
    var criteria = extractTemplateCriteria(dataTemplate, criterionId);
    var group = extractRequirementGroup(criteria, groupId);

    var requirement = group.getOcds().getRequirements().stream()
        .filter(r -> Objects.equals(r.getOcds().getId(), questionId)).findFirst().orElseThrow(
            () -> new ResourceNotFoundException("Question '" + questionId + "' not found"));

    var options = question.getNonOCDS().getOptions();
    // caters for case where no options property is submitted - we will reset
    var updatedOptions = new ArrayList<Option>();

    if (options != null && !options.isEmpty()) {
      updatedOptions.addAll(options.stream()
          .map(questionNonOCDSOptions -> Requirement.Option.builder()
              .select(questionNonOCDSOptions.getSelected() == null ? Boolean.FALSE
                  : questionNonOCDSOptions.getSelected())
              .value(questionNonOCDSOptions.getValue()).text(questionNonOCDSOptions.getText())
              .build())
          .collect(Collectors.toList()));
    }

    requirement.getNonOCDS().updateOptions(updatedOptions);

    // Update Jaggaer Technical Envelope (only for Supplier questions)
    if (Party.TENDERER == criteria.getRelatesTo()) {
      var rfx = createTechnicalEnvelopeUpdateRfx(question, event, requirement);
      var createRfxResponse =
          ofNullable(jaggaerWebClient.post().uri(jaggaerAPIConfig.getCreateRfx().get(ENDPOINT))
              .bodyValue(new CreateUpdateRfx(OperationCode.UPDATE, rfx)).retrieve()
              .bodyToMono(CreateUpdateRfxResponse.class)
              .block(ofSeconds(jaggaerAPIConfig.getTimeoutDuration())))
                  .orElseThrow(() -> new JaggaerApplicationException(INTERNAL_SERVER_ERROR.value(),
                      "Unexpected error updating Rfx"));

      if (createRfxResponse.getReturnCode() != 0
          || !Constants.OK.equals(createRfxResponse.getReturnMessage())) {
        log.error(createRfxResponse.toString());
        throw new JaggaerApplicationException(createRfxResponse.getReturnCode(),
            createRfxResponse.getReturnMessage());
      }
      log.info("Updated event: {}", createRfxResponse);
    }

    // Update Tenders DB
    event.setProcurementTemplatePayload(dataTemplate);
    retryableTendersDBDelegate.save(event);

    return convertRequirementToQuestion(requirement);
  }

  private DataTemplate retrieveDataTemplate(final ProcurementEvent event) {
    DataTemplate dataTemplate;

    // If the template has been persisted, get it from the local database
    if (event.getProcurementTemplatePayload() != null) {
      dataTemplate = event.getProcurementTemplatePayload();
    } else {
      var lotEventTypeDataTemplates =
          agreementsService.getLotEventTypeDataTemplates(event.getProject().getCaNumber(),
              event.getProject().getLotNumber(), ViewEventType.fromValue(event.getEventType()));

      // TODO: Decide how to handle multiple data templates being returned by AS
      dataTemplate = lotEventTypeDataTemplates.stream().findFirst().orElseThrow(
          () -> new AgreementsServiceApplicationException(ERR_MSG_DATA_TEMPLATE_NOT_FOUND));
    }
    return dataTemplate;
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

  /**
   * Rough first cut of code that adds Technical Envelope questions into Jaggaer. This will build an
   * Rfx object containing the Technical Envelope that can be sent to update an existing Rfx in
   * Jaggaer.
   *
   * Current behaviour - it will add questions it does not already have, but will not add
   * duplicates. Questions are not deleted - needs investigation.
   *
   * 'Mandatory' and 'description' fields are not supplied so cannot be completed. Id's are not
   * supplied so cannot update existing.
   */
  private Rfx createTechnicalEnvelopeUpdateRfx(final Question question,
      final ProcurementEvent event, final Requirement requirement) {

    var questionType = TechEnvelopeQuestionType.TEXT; // maps to this Jaggaer type
    var sectionName = "Tender Response"; // default existing section
    var sectionQuestionType = "LOCAL";
    var sectionType = "TECH";

    // only Value question types are supported at present
    if ("Value".equals(requirement.getNonOCDS().getQuestionType())) {
      var rfxSetting = RfxSetting.builder().rfxId(event.getExternalEventId()).build();
      var parameterList = TechEnvelopeParameterList.builder()
          .parameters(question.getNonOCDS().getOptions().stream().map(
              q -> TechEnvelopeParameter.builder().name(q.getValue()).type(questionType).build())
              .collect(Collectors.toList()))
          .build();
      var section = TechEnvelopeSection.builder().name(sectionName).type(sectionType)
          .questionType(sectionQuestionType).parameterList(parameterList).build();
      var techEnvelope = TechEnvelope.builder().sections(Arrays.asList(section)).build();

      return Rfx.builder().rfxSetting(rfxSetting).techEnvelope(techEnvelope).build();

    }
    throw new IllegalArgumentException("Question type of '"
        + requirement.getNonOCDS().getQuestionType() + "' is not currently supported");
  }

  private Question convertRequirementToQuestion(final Requirement r) {

    // TODO: Move to object mapper or similar
    // @formatter:off
    var questionNonOCDS = new QuestionNonOCDS()
        .questionType(QuestionTypeEnum.fromValue(r.getNonOCDS().getQuestionType()))
        .mandatory(r.getNonOCDS().getMandatory())
        .multiAnswer(r.getNonOCDS().getMultiAnswer())
        .answered(r.getNonOCDS().getAnswered())
        .options(ofNullable(r.getNonOCDS().getOptions()).orElseGet(List::of).stream().map(o ->
            new QuestionNonOCDSOptions().value(o.getValue())
                     .selected(o.getSelect() == null? Boolean.FALSE:o.getSelect())
                .text(o.getText())).collect(Collectors.toList()));

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

  }
}
