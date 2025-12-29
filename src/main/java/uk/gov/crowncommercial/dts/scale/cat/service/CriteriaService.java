package uk.gov.crowncommercial.dts.scale.cat.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import uk.gov.crowncommercial.dts.scale.cat.config.Constants;
import uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.exception.AgreementsServiceApplicationException;
import uk.gov.crowncommercial.dts.scale.cat.exception.JaggaerApplicationException;
import uk.gov.crowncommercial.dts.scale.cat.exception.ResourceNotFoundException;
import uk.gov.crowncommercial.dts.scale.cat.mapper.DependencyMapper;
import uk.gov.crowncommercial.dts.scale.cat.mapper.TimelineDependencyMapper;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.Requirement;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.*;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.RequirementGroup;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.Requirement.Option;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.QuestionType;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.*;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.*;
import uk.gov.crowncommercial.dts.scale.cat.processors.DataTemplateProcessor;
import uk.gov.crowncommercial.dts.scale.cat.processors.ProcurementEventHelperService;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.*;
import java.util.stream.Collectors;

import static java.time.Duration.ofSeconds;
import static java.util.Optional.ofNullable;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig.ENDPOINT;

/**
 *
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CriteriaService {

  static final String LOG_TAG = "12322912 - ";
  static final String ERR_MSG_DATA_TEMPLATE_NOT_FOUND = "Data template not found";
  private static final String END_DATE = "##END_DATE##";
  private static final String MONETARY_QUESTION_TYPE = "Monetary";
  private static final String KEYVAL_PAIR_QUESTION_TYPE = "KeyValuePair";
  private static final String TERMS_ACRONYMS_QUESTION_ID = "Question 1";

  private final AgreementsService agreementsService;
  private final ValidationService validationService;
  private final RetryableTendersDBDelegate retryableTendersDBDelegate;
  private final JaggaerAPIConfig jaggaerAPIConfig;
  private final WebClient jaggaerWebClient;
  private final DependencyMapper dependencyMapper;

  private final TimelineDependencyMapper timelineDependencyMapper;


  private final DataTemplateProcessor templateProcessor;
  private final ProcurementEventHelperService eventHelperService;
  private final QuestionAndAnswerService questionAndAnswerService;

  @Transactional
  public Set<EvalCriteria> getEvalCriteria(final Integer projectId, final String eventId,
      final boolean populateGroups) {

    log.debug(LOG_TAG + "Get project from tenders DB to obtain Jaggaer project id");
    // Get project from tenders DB to obtain Jaggaer project id
    var event = validationService.validateProjectAndEventIds(projectId, eventId);
    var dataTemplate = retrieveDataTemplate(event);
    log.debug(LOG_TAG + "retrieveDataTemplate successfully. dataTemplate: {}", dataTemplate);
    // Convert to EvalCriteria and return
    if (populateGroups) {
      log.debug(LOG_TAG + "Populate group is true, let's convert data template to EvalCriteria");
      var eventCriteria = dataTemplate.getCriteria().stream()
          .map(tc -> new EvalCriteria().id(tc.getId()).description(tc.getDescription())
              .description(tc.getDescription()).title(tc.getTitle()).requirementGroups(
                  new ArrayList<>(getEvalCriterionGroups(projectId, eventId, tc.getId(), true))))
          .collect(Collectors.toSet());

      log.debug(LOG_TAG + "Event criteria: {}", eventCriteria);
      return eventCriteria;
    }

    var transformToDto =  dataTemplate
        .getCriteria().stream().map(tc -> new EvalCriteria().id(tc.getId())
            .description(tc.getDescription()).description(tc.getDescription()).title(tc.getTitle()))
        .collect(Collectors.toSet());

    log.debug(LOG_TAG + "Transformed DTO: {}", transformToDto);

    return transformToDto;
  }

  public Set<QuestionGroup> getEvalCriterionGroups(final Integer projectId, final String eventId,
      final String criterionId, final boolean populateRequirements) {
    var event = validationService.validateProjectAndEventIds(projectId, eventId);
    var dataTemplate = retrieveDataTemplate(event);
    var criteria = extractTemplateCriteria(dataTemplate, criterionId);

    var questionGroup =  criteria.getRequirementGroups().stream().map(rg -> {
      // NonOCDS
      var questionGroupNonOCDS = new QuestionGroupNonOCDS().task(rg.getNonOCDS().getTask())
          .order(rg.getNonOCDS().getOrder()).prompt(rg.getNonOCDS().getPrompt())
          .mandatory(rg.getNonOCDS().getMandatory());

      log.debug(LOG_TAG + "NonOCDS: {}", questionGroupNonOCDS);
      // OCDS
      var requirements =
          populateRequirements
              ? convertRequirementsToQuestions(rg.getOcds().getRequirements(),
                  event.getProject().getCaNumber())
              : null;

      log.debug(LOG_TAG + "requirements: {}", requirements);

      var questionGroupOCDS = new QuestionGroupOCDS().id(rg.getOcds().getId())
          .description(rg.getOcds().getDescription()).requirements(requirements);

      log.debug(LOG_TAG + "questionGroupOCDS: {}", questionGroupOCDS);

      var qg =  new QuestionGroup().nonOCDS(questionGroupNonOCDS).OCDS(questionGroupOCDS);
      log.debug(LOG_TAG + "questionGroup: {}", qg);
      return qg;
    }).collect(Collectors.toSet());

    log.debug(LOG_TAG + "questionGroup: {}", questionGroup);
    return questionGroup;
  }

  public Set<Question> getEvalCriterionGroupQuestions(final Integer projectId, final String eventId,
      final String criterionId, final String groupId) {
    var event = validationService.validateProjectAndEventIds(projectId, eventId);
    var dataTemplate = retrieveDataTemplate(event);
    var criteria = extractTemplateCriteria(dataTemplate, criterionId);
    var group = extractRequirementGroup(criteria, groupId);
    var question =  group.getOcds().getRequirements().stream().map(
        (final Requirement r) -> convertRequirementToQuestion(r, event.getProject().getCaNumber()))
        .collect(Collectors.toSet());

    log.debug(LOG_TAG + "question: {}", question);
    return question;

  }


  @Transactional
  public Question putQuestionOptionDetails(final Question question, final Integer projectId,
      final String eventId, final String criterionId, final String groupId,
      final String questionId) {

    // Get the project/event and check if there is a pre-existing event.procurement_template_payload
    var event = validationService.validateProjectAndEventIds(projectId, eventId);
    log.debug(LOG_TAG + "event: {}", event);
    var dataTemplate = retrieveDataTemplate(event);
    log.debug(LOG_TAG + "dataTemplate: {}", dataTemplate);
    var criteria = extractTemplateCriteria(dataTemplate, criterionId);
    log.debug(LOG_TAG + "criteria: {}", criteria);
    var group = extractRequirementGroup(criteria, groupId);
    log.debug(LOG_TAG + "group: {}", group);

    var requirement = group.getOcds().getRequirements().stream()
        .filter(r -> Objects.equals(r.getOcds().getId(), questionId)).findFirst().orElseThrow(
            () -> new ResourceNotFoundException("Question '" + questionId + "' not found"));

    log.debug(LOG_TAG + "requirement: {}", requirement);

    eventHelperService.checkValidforUpdate(requirement);

    var options = question.getNonOCDS().getOptions();
    log.debug(LOG_TAG + "options: {}", options);
    if (options == null) {
      log.error(LOG_TAG +  "'options' property not included in request for event {}", eventId);
      throw new IllegalArgumentException("'options' property must be included in the request");
    }

    if(null != question.getNonOCDS() && null != question.getNonOCDS().getAnswered()){
      requirement.getNonOCDS().setAnswered(question.getNonOCDS().getAnswered());
    }
    if(null != question.getNonOCDS().getTimelineDependency() && null != question.getNonOCDS().getTimelineDependency().getNonOCDS().getOptions()){
             requirement.getNonOCDS().getTimelineDependency().getNonOCDS().updateOptions(getUpdatedOptions(question.getNonOCDS().getTimelineDependency().getNonOCDS().getOptions()));
             requirement.getNonOCDS().getTimelineDependency().getNonOCDS().setAnswered(question.getNonOCDS().getTimelineDependency().getNonOCDS().getAnswered());
    }
    validateQuestionsValues(group, requirement, options);
    requirement.getNonOCDS()
        .updateOptions(getUpdatedOptions(options));

    // Update Jaggaer Technical Envelope (only for Supplier questions)
    if (Party.TENDERER == criteria.getRelatesTo()) {
      var rfx = createTechnicalEnvelopeUpdateRfx(question, event, requirement);

      log.info(LOG_TAG + "Start calling Jaggaer API to update rfx, Rfx Id: {}", rfx.getRfxSetting().getRfxId());
      var createRfxResponse =
          ofNullable(jaggaerWebClient.post().uri(jaggaerAPIConfig.getCreateRfx().get(ENDPOINT))
              .bodyValue(new CreateUpdateRfx(OperationCode.UPDATE, rfx)).retrieve()
              .bodyToMono(CreateUpdateRfxResponse.class)
              .block(ofSeconds(jaggaerAPIConfig.getTimeoutDuration())))
                  .orElseThrow(() -> new JaggaerApplicationException(INTERNAL_SERVER_ERROR.value(),
                      "Unexpected error updating Rfx"));
      log.info(LOG_TAG + "Finish calling Jaggaer API to update rfx, Rfx Id: {}", rfx.getRfxSetting().getRfxId());

      log.debug(LOG_TAG + "createRfxResponse: {}", createRfxResponse);
      if (createRfxResponse.getReturnCode() != 0
          || !Constants.OK_MSG.equals(createRfxResponse.getReturnMessage())) {
        log.error(LOG_TAG + "Jaggaer response was not OK" + createRfxResponse);
        throw new JaggaerApplicationException(createRfxResponse.getReturnCode(),
            createRfxResponse.getReturnMessage());
      }
      log.info(LOG_TAG + "Updated event: {}", createRfxResponse);
    }

    // Update Tenders DB
    event.setProcurementTemplatePayload(dataTemplate);
    event.setUpdatedAt(Instant.now());
    retryableTendersDBDelegate.save(event);
    log.debug(LOG_TAG + "Event saved into the tender DB. event: {}", event);
    var transformRequirementToQuestion =  convertRequirementToQuestion(requirement, event.getProject().getCaNumber());
    log.debug(LOG_TAG + "Successfully transformed requirement to question. question: {}", transformRequirementToQuestion);
    return transformRequirementToQuestion;
  }


  public void validateQuestionsValues(RequirementGroup group, Requirement requirement,
      List<QuestionNonOCDSOptions> options) {
    if (Objects.equals(requirement.getNonOCDS().getQuestionType(), MONETARY_QUESTION_TYPE)) {
      String maxValue = null;
      String minValue = null;
      if (Objects.nonNull(requirement.getNonOCDS().getDependency()) && Objects.nonNull(requirement.getNonOCDS().getDependency().getRelationships())) {
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

        // If the previous question is not Monetary, which is now possible, then we need to cleanse the minValue variable, as the API is designed to assume this is always Monetary, when now it might not be.
        if (minValue != null) {
          try {
            new BigDecimal(minValue);
          } catch(NumberFormatException e){
            minValue = null;
          }
        }
      }
      if (ObjectUtils.allNotNull(maxValue, minValue)) {
        validationService.validateMinMaxValue(new BigDecimal(maxValue), new BigDecimal(minValue));
      }
    } else if (Objects.equals(requirement.getNonOCDS().getQuestionType().toUpperCase(), KEYVAL_PAIR_QUESTION_TYPE.toUpperCase()) && Objects.equals(requirement.getOcds().getId().toUpperCase(), TERMS_ACRONYMS_QUESTION_ID.toUpperCase())) {
      if (options.size() > 20) {
        log.error(LOG_TAG + "Too many values. Maximum allowed is 20.");
        throw new IllegalArgumentException("Too many values. Maximum allowed is 20.");
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
      log.debug(LOG_TAG + "Template has been persisted, getting from the local database. dataTemplate: {}", dataTemplate);
    } else {
        var legacyFlow = false; // While new Q and A flow is broken and being fixed (NCAS-795), revert and use the legacy flow.
        List<DataTemplate> lotEventTypeDataTemplates;

        if (legacyFlow) {
          log.debug(LOG_TAG + "Getting template data from agreement service as legacyFlow is true.");
          lotEventTypeDataTemplates =
            agreementsService.getLotEventTypeDataTemplates(event.getProject().getCaNumber(),
            event.getProject().getLotNumber(), ViewEventType.fromValue(event.getEventType()));
        } else {
          log.debug(LOG_TAG + "Getting template data from Q&A service.");
          // NCAS- 795, should retrieve Questions and answers from new Question and answer service
          lotEventTypeDataTemplates =
            questionAndAnswerService.getLotEventTypeDataTemplates(event.getProject().getCaNumber(),
            event.getProject().getLotNumber(), ViewEventType.fromValue(event.getEventType()));
        }

        if(null == event.getTemplateId()) {
          log.debug(LOG_TAG + "Getting single data template object from Data template collection");
          dataTemplate = lotEventTypeDataTemplates.stream().findFirst().orElseThrow(
                  () -> new AgreementsServiceApplicationException(ERR_MSG_DATA_TEMPLATE_NOT_FOUND + " + Single data template error +"));

          log.debug(LOG_TAG + "Single data template: {}", dataTemplate);
        }  else {
          log.debug(LOG_TAG + "Find template with matching templateId");
          String errorLog = ERR_MSG_DATA_TEMPLATE_NOT_FOUND + " event.getTemplateId(): " + event.getTemplateId();
          dataTemplate = lotEventTypeDataTemplates.stream()
                  .filter(t -> null != t.getId() && t.getId().equals(event.getTemplateId()))
                  .findFirst()
                  .map(t -> {
                    log.debug(LOG_TAG + "templateId from lotEventTypeDataTemplates matched, templatedId: " + t.getId());
                    return t;
                  })
                  .orElseThrow(() -> new AgreementsServiceApplicationException(errorLog));

          log.debug(LOG_TAG + "Template with matching templateId, {}", dataTemplate);

          if(null != dataTemplate.getParent()) {
            log.debug(LOG_TAG + "Data template parent is not null.");
            Optional<ProcurementEvent> optionalProcurementEvent =  eventHelperService.getParentEvent(event, dataTemplate.getParent());
            if(optionalProcurementEvent.isPresent()){
              log.debug(LOG_TAG + "optionalProcurementEvent.isPresent().");
              DataTemplate oldTemplate = optionalProcurementEvent.get().getProcurementTemplatePayload();
              dataTemplate = templateProcessor.process(dataTemplate, oldTemplate);
              log.debug(LOG_TAG + "Successfully processed data template. dataTemplate: {}", dataTemplate);
            }else{
              //TODO   throw exception or leave as it is ??
              log.error(LOG_TAG + "Parent data template is empty");
              throw new RuntimeException("Parent event with templateId " + dataTemplate.getParent() + " is not found");
            }
          }
        }

        event.setProcurementTemplatePayload(dataTemplate);
        event.setUpdatedAt(Instant.now());
        retryableTendersDBDelegate.save(event);
        log.debug(LOG_TAG + "Saved event details into the tenders DB. event: {}", event);
    }

    log.debug(LOG_TAG + "Returning from retrieveDataTemplate method. dataTemplate: {}", dataTemplate);
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
     * <p>
     * Current behaviour - it will add questions it does not already have, but will not add
     * duplicates. Questions are not deleted - needs investigation.
     * <p>
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
      log.debug(LOG_TAG + "rfxSetting: {}", rfxSetting);
      var parameterList = TechEnvelopeParameterList.builder()
          .parameters(question.getNonOCDS().getOptions().stream().map(
              q -> TechEnvelopeParameter.builder().name(q.getValue()).type(questionType).build())
              .collect(Collectors.toList()))
          .build();
      log.debug(LOG_TAG + "parameterList: {}", parameterList);
      var section = TechEnvelopeSection.builder().name(sectionName).type(sectionType)
          .questionType(sectionQuestionType).parameterList(parameterList).build();
      log.debug(LOG_TAG + "section: {}", section);
      var techEnvelope = TechEnvelope.builder().sections(Arrays.asList(section)).build();
      log.debug(LOG_TAG + "techEnvelope: {}", techEnvelope);

      return Rfx.builder().rfxSetting(rfxSetting).techEnvelope(techEnvelope).build();

    }
    throw new IllegalArgumentException("Question type of '"
        + requirement.getNonOCDS().getQuestionType() + "' is not currently supported");
  }

  public List<Question> convertRequirementsToQuestions(final Set<Requirement> requirements,
      final String agreementNumber) {
    var requirementToQuestion = requirements.stream()
        .map((final Requirement requirement) -> convertRequirementToQuestion(requirement,
            agreementNumber))
        .collect(Collectors.toList());

    log.debug(LOG_TAG + "requirementToQuestion: {}", requirementToQuestion);
    return requirementToQuestion;
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
    log.debug(LOG_TAG + "questionNonOCDS: {}", questionNonOCDS);
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

      log.debug(LOG_TAG + "aggrementDetails: {}", agreementDetails);
    }
    var questionOCDS = new Requirement1()
        .id(r.getOcds().getId())
        .title(r.getOcds().getTitle())
        .description(description)
        .dataType(DataType.fromValue(r.getOcds().getDataType().toLowerCase()))
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
    log.debug(LOG_TAG + "questionOCDS: {}", questionOCDS);
    var questionAfterConversion = new Question().nonOCDS(questionNonOCDS).OCDS(questionOCDS);
    log.debug(LOG_TAG + "questionAfterConversion: {}", questionAfterConversion);
    return questionAfterConversion;

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
    log.debug(LOG_TAG + "questionNonOCDSOptions: {}", questionNonOCDSOptions);
    return questionNonOCDSOptions;
  }

  private static List<Option> getUpdatedOptions(List<QuestionNonOCDSOptions> options) {
    var updatedOption =  options.stream()
            .map(questionNonOCDSOptions -> Option.builder()
                    .select(questionNonOCDSOptions.getSelected() == null ? Boolean.FALSE
                            : questionNonOCDSOptions.getSelected())
                    .value(questionNonOCDSOptions.getValue()).text(questionNonOCDSOptions.getText())
                    .tableDefinition(questionNonOCDSOptions.getTableDefinition()).build())
            .collect(Collectors.toList());
    log.debug(LOG_TAG + "updatedOption: {}", updatedOption);
    return updatedOption;
  }
}
