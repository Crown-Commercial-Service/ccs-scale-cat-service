package uk.gov.crowncommercial.dts.scale.cat.service;

import static java.time.Duration.ofSeconds;
import static java.util.Optional.ofNullable;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig.ENDPOINT;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.SerializationUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.config.Constants;
import uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.exception.AgreementsServiceApplicationException;
import uk.gov.crowncommercial.dts.scale.cat.exception.JaggaerApplicationException;
import uk.gov.crowncommercial.dts.scale.cat.exception.ResourceNotFoundException;
import uk.gov.crowncommercial.dts.scale.cat.mapper.DependencyMapper;
import uk.gov.crowncommercial.dts.scale.cat.mapper.TimelineDependencyMapper;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.DataTemplate;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.Party;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.Relationships;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.Requirement;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.Requirement.Option;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.RequirementGroup;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.TemplateCriteria;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.*;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.QuestionType;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.*;
import uk.gov.crowncommercial.dts.scale.cat.processors.DataTemplateProcessor;
import uk.gov.crowncommercial.dts.scale.cat.processors.ProcurementEventHelperService;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;

import javax.transaction.Transactional;

/**
 *
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CriteriaService {

  static final String ERR_MSG_DATA_TEMPLATE_NOT_FOUND = "Data template not found";
  private static final String END_DATE = "##END_DATE##";
  private static final String MONETARY_QUESTION_TYPE = "Monetary";

  private final AgreementsService agreementsService;
  private final ValidationService validationService;
  private final RetryableTendersDBDelegate retryableTendersDBDelegate;
  private final JaggaerAPIConfig jaggaerAPIConfig;
  private final WebClient jaggaerWebClient;
  private final DependencyMapper dependencyMapper;

  private final TimelineDependencyMapper timelineDependencyMapper;


  private final DataTemplateProcessor templateProcessor;
  private final ProcurementEventHelperService eventHelperService;

  @Transactional
  public Set<EvalCriteria> getEvalCriteria(final Integer projectId, final String eventId,
      final boolean populateGroups) {

    // Get project from tenders DB to obtain Jaggaer project id
    var event = validationService.validateProjectAndEventIds(projectId, eventId);
    var dataTemplate = retrieveDataTemplate(event);

    // Convert to EvalCriteria and return
    if (populateGroups) {
      return dataTemplate.getCriteria().stream()
          .map(tc -> new EvalCriteria().id(tc.getId()).description(tc.getDescription())
              .description(tc.getDescription()).title(tc.getTitle()).requirementGroups(
                  new ArrayList<>(getEvalCriterionGroups(projectId, eventId, tc.getId(), true))))
          .collect(Collectors.toSet());
    }
    return dataTemplate
        .getCriteria().stream().map(tc -> new EvalCriteria().id(tc.getId())
            .description(tc.getDescription()).description(tc.getDescription()).title(tc.getTitle()))
        .collect(Collectors.toSet());
  }

  public Set<QuestionGroup> getEvalCriterionGroups(final Integer projectId, final String eventId,
      final String criterionId, final boolean populateRequirements) {
    var event = validationService.validateProjectAndEventIds(projectId, eventId);
    var dataTemplate = retrieveDataTemplate(event);
    var criteria = extractTemplateCriteria(dataTemplate, criterionId);

    return criteria.getRequirementGroups().stream().map(rg -> {
      // NonOCDS
      var questionGroupNonOCDS = new QuestionGroupNonOCDS().task(rg.getNonOCDS().getTask())
          .order(rg.getNonOCDS().getOrder()).prompt(rg.getNonOCDS().getPrompt())
          .mandatory(rg.getNonOCDS().getMandatory());
      // OCDS
      var requirements =
          populateRequirements
              ? convertRequirementsToQuestions(rg.getOcds().getRequirements(),
                  event.getProject().getCaNumber())
              : null;
      var questionGroupOCDS = new QuestionGroupOCDS().id(rg.getOcds().getId())
          .description(rg.getOcds().getDescription()).requirements(requirements);

      return new QuestionGroup().nonOCDS(questionGroupNonOCDS).OCDS(questionGroupOCDS);
    }).collect(Collectors.toSet());
  }

  public Set<Question> getEvalCriterionGroupQuestions(final Integer projectId, final String eventId,
      final String criterionId, final String groupId) {
    var event = validationService.validateProjectAndEventIds(projectId, eventId);
    var dataTemplate = retrieveDataTemplate(event);
    var criteria = extractTemplateCriteria(dataTemplate, criterionId);
    var group = extractRequirementGroup(criteria, groupId);
    return group.getOcds().getRequirements().stream().map(
        (final Requirement r) -> convertRequirementToQuestion(r, event.getProject().getCaNumber()))
        .collect(Collectors.toSet());

  }


  @Transactional
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

    eventHelperService.checkValidforUpdate(requirement);

    var options = question.getNonOCDS().getOptions();
    if (options == null) {
      log.error("'options' property not included in request for event {}", eventId);
      throw new IllegalArgumentException("'options' property must be included in the request");
    }

    if(null != question.getNonOCDS() && null != question.getNonOCDS().getAnswered()){
      requirement.getNonOCDS().setAnswered(question.getNonOCDS().getAnswered());
    }
    if(null != question.getNonOCDS().getTimelineDependency()){
             requirement.getNonOCDS().getTimelineDependency().getNonOCDS().updateOptions(getUpdatedOptions(options));
             requirement.getNonOCDS().getTimelineDependency().getNonOCDS().setAnswered(question.getNonOCDS().getAnswered());
    }
    validateQuestionsValues(group, requirement, options);
    requirement.getNonOCDS()
        .updateOptions(getUpdatedOptions(options));

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
          || !Constants.OK_MSG.equals(createRfxResponse.getReturnMessage())) {
        log.error(createRfxResponse.toString());
        throw new JaggaerApplicationException(createRfxResponse.getReturnCode(),
            createRfxResponse.getReturnMessage());
      }
      log.info("Updated event: {}", createRfxResponse);
    }

    // Update Tenders DB
    event.setProcurementTemplatePayload(dataTemplate);
    event.setUpdatedAt(Instant.now());
    retryableTendersDBDelegate.save(event);

    return convertRequirementToQuestion(requirement, event.getProject().getCaNumber());
  }

  private static List<Option> getUpdatedOptions(List<QuestionNonOCDSOptions> options) {
    return options.stream()
            .map(questionNonOCDSOptions -> Option.builder()
                    .select(questionNonOCDSOptions.getSelected() == null ? Boolean.FALSE
                            : questionNonOCDSOptions.getSelected())
                    .value(questionNonOCDSOptions.getValue()).text(questionNonOCDSOptions.getText())
                    .tableDefinition(questionNonOCDSOptions.getTableDefinition()).build())
            .collect(Collectors.toList());
  }


  public void validateQuestionsValues(RequirementGroup group, Requirement requirement,
      List<QuestionNonOCDSOptions> options) {
    if (Objects.equals(requirement.getNonOCDS().getQuestionType(), MONETARY_QUESTION_TYPE)) {
      String maxValue;
      String minValue;
      if (Objects.nonNull(requirement.getNonOCDS().getDependency())) {
        // Min value check
        String questionId = requirement.getNonOCDS().getDependency().getRelationships().stream()
            .map(Relationships::getDependentOnID).findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("Max value question not found"));
        var maxValueRequirement = group.getOcds().getRequirements().stream()
            .filter(question -> Objects.equals(question.getOcds().getId(), questionId)).findFirst()
            .orElseThrow(
                () -> new ResourceNotFoundException("Question '" + questionId + "' not found"));
        maxValue = getOptionsValue(maxValueRequirement.getNonOCDS().getOptions());
        minValue = options.stream().findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("Requested Value should not be null"))
            .getValue();
      } else {
        // Max value check
        var minValueRequirement = group.getOcds().getRequirements().stream()
            .filter(question -> !question.getOcds().getId().equals(requirement.getOcds().getId()))
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("Min value question not found"));
        maxValue = options.stream().findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("Requested Value should not be null"))
            .getValue();
        minValue = getOptionsValue(minValueRequirement.getNonOCDS().getOptions());
      }
      if (ObjectUtils.allNotNull(maxValue, minValue)) {
        validationService.validateMinMaxValue(new BigDecimal(maxValue), new BigDecimal(minValue));
      }
    }
  }

  private String getOptionsValue(List<Option> options) {
    if (Objects.isNull(options)) {
      return null;
    }
    var option = options.stream().findFirst()
        .orElseThrow(() -> new ResourceNotFoundException("Option value not found"));
    return option.getValue();
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
        if(null == event.getTemplateId())
          dataTemplate = lotEventTypeDataTemplates.stream().findFirst().orElseThrow(
              () -> new AgreementsServiceApplicationException(ERR_MSG_DATA_TEMPLATE_NOT_FOUND));
        else{
          dataTemplate = lotEventTypeDataTemplates.stream().filter(t -> (null != t.getId() &&
                  t.getId().equals(event.getTemplateId()))).findFirst().orElseThrow(
                    () -> new AgreementsServiceApplicationException(ERR_MSG_DATA_TEMPLATE_NOT_FOUND));

          if(null != dataTemplate.getParent()) {
            Optional<ProcurementEvent> optionalProcurementEvent =  eventHelperService.getParentEvent(event, dataTemplate.getParent());
            if(optionalProcurementEvent.isPresent()){
              DataTemplate oldTemplate = optionalProcurementEvent.get().getProcurementTemplatePayload();
              dataTemplate = templateProcessor.process(dataTemplate, oldTemplate);
            }else{
              //TODO   throw exception or leave as it is ??
              log.info("Parent data template is empty");
              throw new RuntimeException("Parent event with templateId " + dataTemplate.getParent() + " is not found");
            }
          }
        }

        event.setProcurementTemplatePayload(dataTemplate);
        event.setUpdatedAt(Instant.now());
        retryableTendersDBDelegate.save(event);
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

  public List<Question> convertRequirementsToQuestions(final Set<Requirement> requirements,
      final String agreementNumber) {
    return requirements.stream()
        .map((final Requirement requirement) -> convertRequirementToQuestion(requirement,
            agreementNumber))
        .collect(Collectors.toList());
  }

  public Question convertRequirementToQuestion(final Requirement r, final String agreementNumber) {

    // TODO: Move to object mapper or similar
    // @formatter:off
    var questionNonOCDS = new QuestionNonOCDS()
        .questionType(QuestionType.fromValue(r.getNonOCDS().getQuestionType()))
        .mandatory(r.getNonOCDS().getMandatory())
        .multiAnswer(r.getNonOCDS().getMultiAnswer())
        .length(r.getNonOCDS().getLength())
            .inheritance(r.getNonOCDS().getInheritance())
        .answered(r.getNonOCDS().getAnswered()).order(r.getNonOCDS().getOrder())
        .options(ofNullable(r.getNonOCDS().getOptions()).orElseGet(List::of).stream()
            .map(this::getQuestionNonOCDSOptions
        ).collect(Collectors.toList()));
    if (Objects.nonNull(r.getNonOCDS().getDependency())) {
      questionNonOCDS.dependency(dependencyMapper.convertToQuestionNonOCDSDependency(r));
    }
    if (Objects.nonNull(r.getNonOCDS().getTimelineDependency())) {
      questionNonOCDS.timelineDependency(timelineDependencyMapper.convertToTimelineDependency(r));
    }

    var description = r.getOcds().getDescription();
    if (Objects.nonNull(description) && description.contains(END_DATE)) {
      var agreementDetails = agreementsService.getAgreementDetails(agreementNumber);
      description = description.replaceAll(END_DATE,
              DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).format(agreementDetails.getEndDate()));
    }
    var questionOCDS = new Requirement1()
        .id(r.getOcds().getId())
        .title(r.getOcds().getTitle())
        .description(description)
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

  private QuestionNonOCDSOptions getQuestionNonOCDSOptions(Requirement.Option o) {
    QuestionNonOCDSOptions questionNonOCDSOptions = new QuestionNonOCDSOptions().value(o.getValue())
        .selected(o.getSelect() == null ? Boolean.FALSE : o.getSelect()).text(o.getText());

    if (o.getTableDefinition() != null) {
      questionNonOCDSOptions.tableDefinition(
          new TableDefinition().editableRows(o.getTableDefinition().getEditableRows())
              .editableCols(o.getTableDefinition().getEditableCols())
              .titles(o.getTableDefinition().getTitles()).data(o.getTableDefinition().getData()));
    }
    return questionNonOCDSOptions;
  }
}
