package uk.gov.crowncommercial.dts.scale.cat.service;

import static java.time.Duration.ofSeconds;
import static java.util.Optional.ofNullable;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig.ENDPOINT;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.jena.atlas.logging.Log;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import uk.gov.crowncommercial.dts.scale.cat.config.Constants;
import uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.exception.AgreementsServiceApplicationException;
import uk.gov.crowncommercial.dts.scale.cat.exception.JaggaerApplicationException;
import uk.gov.crowncommercial.dts.scale.cat.exception.ResourceNotFoundException;
import uk.gov.crowncommercial.dts.scale.cat.mapper.DependencyMapper;
import uk.gov.crowncommercial.dts.scale.cat.mapper.ProcurementEventMapper;
import uk.gov.crowncommercial.dts.scale.cat.mapper.TimelineDependencyMapper;
import uk.gov.crowncommercial.dts.scale.cat.model.OCID;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.DataTemplate;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.Party;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.Relationships;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.Requirement;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.Requirement.Option;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.RequirementGroup;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.TemplateCriteria;
import uk.gov.crowncommercial.dts.scale.cat.model.cas.generated.StageNameRead;
import uk.gov.crowncommercial.dts.scale.cat.model.cas.generated.StagesRead;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementStageEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.DataType;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.EvalCriteria;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.Period1;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.QandA;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.QandAWithProjectDetails;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.Question;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.QuestionGroup;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.QuestionGroupNamesRead;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.QuestionGroupNonOCDS;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.QuestionGroupOCDS;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.QuestionNonOCDS;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.QuestionNonOCDSOptions;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.QuestionType;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.Requirement1;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.TableDefinition;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.Value1;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.ViewEventType;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.CreateUpdateRfx;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.CreateUpdateRfxResponse;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.OperationCode;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.Rfx;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.RfxSetting;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.TechEnvelope;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.TechEnvelopeParameter;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.TechEnvelopeParameterList;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.TechEnvelopeQuestionType;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.TechEnvelopeSection;
import uk.gov.crowncommercial.dts.scale.cat.processors.DataTemplateProcessor;
import uk.gov.crowncommercial.dts.scale.cat.processors.ProcurementEventHelperService;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.*;
import java.util.stream.Collectors;

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

  private static final String COP_GROUP_TYPE = "CoP";
  private static final String AWARD_CRITERIA_GROUP_TYPE = "award-criteria";

  private static final String ASSESSMENT_CRITERIA_CRITERION_ID = "Criterion 2";
  private static final String COP_GROUP_ID = "Group 1";
  private static final String AWARD_CRITERIA_GROUP_ID = "Group 2";
  private static final String GROUP_ORDER_FIELD = "groupOrder";
  private static final String USE_QUESTION_GROUPS = "use-question-groups";
  private static final String QUESTION_GROUP_PREFIX = "question-group-";
  private static final String STAGE_NUMBER = "stage";
  private static final int DEFAULT_GROUP_ORDER = 0;

  private final AgreementsService agreementsService;
  private final ValidationService validationService;
  private final StageService stageService;
  private final RetryableTendersDBDelegate retryableTendersDBDelegate;
  private final JaggaerAPIConfig jaggaerAPIConfig;
  private final WebClient jaggaerWebClient;
  private final DependencyMapper dependencyMapper;

  private final TimelineDependencyMapper timelineDependencyMapper;

  private final DataTemplateProcessor templateProcessor;
  private final ProcurementEventHelperService eventHelperService;
  private final QuestionAndAnswerService questionAndAnswerService;
  private final ProcurementEventMapper procurementEventMapper;

  @Transactional
  public Set<EvalCriteria> getEvalCriteria(final Integer projectId, final String eventId, final Integer stageNumber, final boolean populateGroups) {

    log.debug(LOG_TAG + "Get project from tenders DB to obtain Jaggaer project id");
    // Get project from tenders DB to obtain Jaggaer project id
    var event = validationService.validateProjectAndEventIds(projectId, eventId, stageNumber);
    var dataTemplate = retrieveDataTemplate(event, stageNumber);
    log.debug(LOG_TAG + "retrieveDataTemplate successfully. dataTemplate: {}", dataTemplate);
    // Convert to EvalCriteria and return
    if (populateGroups) {
      log.debug(LOG_TAG + "Populate group is true, let's convert data template to EvalCriteria");
      var eventCriteria = dataTemplate.getCriteria().stream()
          .map(tc -> new EvalCriteria().id(tc.getId()).description(tc.getDescription())
              .description(tc.getDescription()).title(tc.getTitle()).requirementGroups(
                  new ArrayList<>(getEvalCriterionGroups(projectId, eventId, tc.getId(), stageNumber, true))))
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
      final String criterionId, final Integer stageNumber, final boolean populateRequirements) {
    var event = validationService.validateProjectAndEventIds(projectId, eventId, stageNumber);
    var dataTemplate = retrieveDataTemplate(event, stageNumber);
    var criteria = extractTemplateCriteria(dataTemplate, criterionId);

    var questionGroup =  criteria.getRequirementGroups().stream().map(rg -> {
      // NonOCDS
      var questionGroupNonOCDS = new QuestionGroupNonOCDS().task(rg.getNonOCDS().getTask())
          .order(rg.getNonOCDS().getOrder()).prompt(rg.getNonOCDS().getPrompt())
          .mandatory(rg.getNonOCDS().getMandatory());

      log.debug(LOG_TAG + "NonOCDS: {}", questionGroupNonOCDS);
      // OCDS
      log.debug(LOG_TAG + "populateRequirements set as {}", populateRequirements);
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
      final String criterionId, final String groupId, final Integer stageNumber) {
    var event = validationService.validateProjectAndEventIds(projectId, eventId, stageNumber);
    var dataTemplate = retrieveDataTemplate(event, stageNumber);
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
      final String questionId, final Integer stageNumber) {

    // Get the project/event and check if there is a pre-existing event.procurement_template_payload
    var event = validationService.validateProjectAndEventIds(projectId, eventId, stageNumber);
    log.debug(LOG_TAG + "event: {}", event);
    var dataTemplate = retrieveDataTemplate(event, stageNumber);
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

    requirement.getNonOCDS().updateOptions(getUpdatedOptions(options));

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

    Integer thisStageNumber = null == stageNumber || 0 == stageNumber ? null : stageNumber;

    if (null == thisStageNumber) {
        // we are NOT in multi-stage
        retryableTendersDBDelegate.save(event);
        log.debug(LOG_TAG + "Event saved into the tender DB. event: {}", event);
    } else {
        // we ARE in multi-stage
        ProcurementStageEvent procurementStageEvent = procurementEventMapper.procurementEventToProcurementStageEvent(event);
        procurementStageEvent.setStageNumber(thisStageNumber);

        // add in the stage description (this helps when generating the bid-packs)
        final StagesRead stagesRead = stageService.getStagesForEventId(eventId);

        if (null != stagesRead && null != stagesRead.getStageNames() && !stagesRead.getStageNames().isEmpty()) {
            for (StageNameRead entry : stagesRead.getStageNames()) {
                if (thisStageNumber == entry.getStageNumber()) {
                    procurementStageEvent.setStageDescription(entry.getStageName());
                    break;
                }
            }
        }

        retryableTendersDBDelegate.save(procurementStageEvent);
        log.debug(LOG_TAG + "Event saved into the tender DB. event: {}", procurementStageEvent);
    }

    var transformRequirementToQuestion =  convertRequirementToQuestion(requirement, event.getProject().getCaNumber());
    log.debug(LOG_TAG + "Successfully transformed requirement to question. question: {}", transformRequirementToQuestion);
    return transformRequirementToQuestion;
  }

  public void validateQuestionsValues(RequirementGroup group, Requirement requirement, List<QuestionNonOCDSOptions> options) {
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

  private DataTemplate retrieveDataTemplate(final ProcurementEvent event, final Integer stageNumber) {
    DataTemplate dataTemplate;

    // If the template has been persisted, get it from the local database
    if (event.getProcurementTemplatePayload() != null) {
      dataTemplate = event.getProcurementTemplatePayload();
      log.debug(LOG_TAG + "Template has been persisted, getting from the local database. dataTemplate: {}", dataTemplate);
    } else {
        // Option to manually revert to legacy Agreement Service flow (NCAS-795), if needed.
        // The boolean check here is to see if dos6 is the agreement id, and if it is use the legacy AS flow.
        String dos6AgreementId = "RM1043.8";
        boolean legacyFlow = dos6AgreementId.equalsIgnoreCase(event.getProject().getCaNumber());
        List<DataTemplate> lotEventTypeDataTemplates;

        if (legacyFlow) {
          // For DOS6 we should use legacy AS flow.
          log.debug(LOG_TAG + "Getting template data from agreement service as legacyFlow is true.");
          lotEventTypeDataTemplates =
            agreementsService.getLotEventTypeDataTemplates(event.getProject().getCaNumber(),
            event.getProject().getLotNumber(), ViewEventType.fromValue(event.getEventType()));
        } else {
          log.debug(LOG_TAG + "Getting template data from Q&A service.");
          // NCAS- 795; For non-DOS6 we should retrieve Questions and answers from new Question and answer service.
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

          if (legacyFlow) {
            dataTemplate = lotEventTypeDataTemplates.stream()
                    .filter(t -> null != t.getId() && t.getId().equals(event.getTemplateId()))
                    .findFirst()
                    .map(t -> {
                      log.debug(LOG_TAG + "templateId from lotEventTypeDataTemplates matched, templatedId: " + t.getId());
                      return t;
                    })
                    .orElseThrow(() -> new AgreementsServiceApplicationException(errorLog));
          } else {
              dataTemplate =
                  lotEventTypeDataTemplates.stream()
                      .filter(t -> event.getTemplateId().equals(t.getId()))
                      .reduce((existing, incoming) -> {
                          if (incoming.getCriteria() != null) {
                              existing.getCriteria().addAll(incoming.getCriteria());
                          }
                          return existing;
                      })
                      .orElseThrow(() ->
                          new AgreementsServiceApplicationException(errorLog)
                      );
          }

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

        Integer thisStageNumber = null == stageNumber || 0 == stageNumber ? null : stageNumber;

        if (null == thisStageNumber) {
            // we are NOT in multi-stage
            retryableTendersDBDelegate.save(event);
            log.debug(LOG_TAG + "Event saved into the tender DB. event: {}", event);
        } else {
            // we ARE in multi-stage
            ProcurementStageEvent procurementStageEvent = procurementEventMapper.procurementEventToProcurementStageEvent(event);
            procurementStageEvent.setStageNumber(thisStageNumber);

            // add in the stage description (this helps when generating the bid-packs)
            final StagesRead stagesRead = stageService.getStagesForEventId(event.getEventID());

            if (null != stagesRead && null != stagesRead.getStageNames() && !stagesRead.getStageNames().isEmpty()) {
                for (StageNameRead entry : stagesRead.getStageNames()) {
                    if (thisStageNumber == entry.getStageNumber()) {
                        procurementStageEvent.setStageDescription(entry.getStageName());
                        break;
                    }
                }
            }

            retryableTendersDBDelegate.save(procurementStageEvent);
            log.debug(LOG_TAG + "Event saved into the tender DB. event: {}", procurementStageEvent);
        }
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
    log.debug(LOG_TAG + "convertRequirementToQuestion method, agreementNumber: {}, requirement: {}", agreementNumber, r);
    // TODO: Move to object mapper or similar
    // @formatter:off
    var questionNonOCDS = new QuestionNonOCDS()
        .questionType(QuestionType.fromValue(r.getNonOCDS().getQuestionType()))
        .mandatory(r.getNonOCDS().getMandatory())
        .multiAnswer(r.getNonOCDS().getMultiAnswer())
        .length(r.getNonOCDS().getLength())
            .inheritance(r.getNonOCDS().getInheritance())
        .answered(r.getNonOCDS().getAnswered()).order(r.getNonOCDS().getOrder())
        .options(ofNullable(r.getNonOCDS().getOptions())
                .orElseGet(List::of).stream() //Checks if the options list in the source is null.
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
      log.debug(LOG_TAG + "Getting aggrementDetails from agreement service. aggrementDetails: {}", agreementDetails);
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

  /*
   * This method ensures all the stage descriptions are stored within each ProcurementStageEvent instance,
   * which helps when generating the bid-packs.
   */
  public void ensureStageDescriptionsAreStoredInProcurementStageEvent(final String eventId) {
    final StagesRead stagesRead = stageService.getStagesForEventId(eventId);

    if (null == stagesRead || null == stagesRead.getStageNames() || stagesRead.getStageNames().isEmpty()) {
        return;
    }

    for (StageNameRead entry : stagesRead.getStageNames()) {
        OCID eventOCID = validationService.validateEventId(entry.getEventId());

        Optional<ProcurementStageEvent> procurementStageEvent =
            retryableTendersDBDelegate.findProcurementStageEventByIdAndStageNumberAndOcdsAuthorityNameAndOcidPrefix(
                Integer.valueOf(eventOCID.getInternalId()), entry.getStageNumber(), eventOCID.getAuthority(), eventOCID.getPublisherPrefix());

        if (procurementStageEvent.isPresent()) {
            procurementStageEvent.get().setStageDescription(entry.getStageName());

            retryableTendersDBDelegate.save(procurementStageEvent.get());
        }
    }
  }

  /**
   * Add GroupOrder fields in the JSON template payload, as required by the bid-pack logic.
   *
   * @param projectId
   * @param eventId
   * @param principal
   */
  public void createGroupOrderFieldsInTheJsonTemplatePayloadForBidPack(final Integer projectId, final String eventId, final String principal) {

    final OCID eventOCID = validationService.validateEventId(eventId);

    final StagesRead stagesRead = stageService.getStagesForEventId(eventId);

    if (null == stagesRead || 0 == stagesRead.getNumberOfStages()) {
        // we are not in multi-stage

        // grab the event data
        ProcurementEvent procurementEvent = retryableTendersDBDelegate
            .findProcurementEventByIdAndOcdsAuthorityNameAndOcidPrefix(Integer.valueOf(eventOCID.getInternalId()), eventOCID.getAuthority(), eventOCID.getPublisherPrefix())
            .orElse(null);

        if (null == procurementEvent) {
            return;
        }

        DataTemplate dataTemplate = null == procurementEvent ? null : procurementEvent.getProcurementTemplatePayload();

        if (null == dataTemplate || null == dataTemplate.getCriteria()) {
            return;
        }

        boolean updated = updateDataTemplate(projectId, eventId, principal, dataTemplate, 0);

        if (updated) {
            procurementEvent.setProcurementTemplatePayload(dataTemplate);

            retryableTendersDBDelegate.save(procurementEvent);
        }

        return;
    }

    //
    // we are in multi-stage
    //

    for (int currentStageNumber = 1; currentStageNumber <= stagesRead.getNumberOfStages(); currentStageNumber++) {
        // grab the stage-specific data
        ProcurementStageEvent procurementEventForStage = retryableTendersDBDelegate
            .findProcurementStageEventByIdAndStageNumberAndOcdsAuthorityNameAndOcidPrefix(Integer.valueOf(eventOCID.getInternalId()), currentStageNumber, eventOCID.getAuthority(), eventOCID.getPublisherPrefix())
            .orElse(null);

        if (null == procurementEventForStage) {
            continue;
        }

        DataTemplate dataTemplate = null == procurementEventForStage ? null : procurementEventForStage.getProcurementTemplatePayload();

        if (null == dataTemplate || null == dataTemplate.getCriteria()) {
            continue;
        }

        boolean updated = updateDataTemplate(projectId, eventId, principal, dataTemplate, currentStageNumber);

        if (updated) {
            procurementEventForStage.setStageNumber(currentStageNumber);
            procurementEventForStage.setProcurementTemplatePayload(dataTemplate);

            retryableTendersDBDelegate.save(procurementEventForStage);
        }
    }
  }

  private boolean updateDataTemplate(final Integer projectId, final String eventId, final String principal, DataTemplate dataTemplate, int currentStageNumber) {
      final List<String> copGroupNamesListForStage = getQuestionGroupsForGroupTypeAndStageNumber(projectId, eventId, principal, COP_GROUP_TYPE, currentStageNumber);
      final List<String> awardCriteriaGroupNamesListForStage = getQuestionGroupsForGroupTypeAndStageNumber(projectId, eventId, principal, AWARD_CRITERIA_GROUP_TYPE, currentStageNumber);

      boolean updated = false;

      if (null == copGroupNamesListForStage && null == awardCriteriaGroupNamesListForStage) {
          return updated;
      }

      for (final TemplateCriteria criteria: dataTemplate.getCriteria()) {
          // we are only interested in 'Criterion 2'
          if (null == criteria || null == criteria.getRequirementGroups() || !ASSESSMENT_CRITERIA_CRITERION_ID.equals(criteria.getId())) {
              continue;
          }

          for (final RequirementGroup requirementGroup: criteria.getRequirementGroups()) {
              if (null == requirementGroup || null == requirementGroup.getOcds() || null == requirementGroup.getOcds().getRequirements()) {
                  continue;
              }

              // we are only interested in CoP and Award Criteria groups
              if (!COP_GROUP_ID.equals(requirementGroup.getOcds().getId()) &&
                  !AWARD_CRITERIA_GROUP_ID.equals(requirementGroup.getOcds().getId()) &&
                  !requirementGroup.getOcds().getId().startsWith(COP_GROUP_ID + ".") &&
                  !requirementGroup.getOcds().getId().startsWith(AWARD_CRITERIA_GROUP_ID + ".")) {
                  continue;
              }

              final Set<Requirement> requirements = requirementGroup.getOcds().getRequirements();

              if (null == requirements || requirements.isEmpty()) {
                  continue;
              }

              if (COP_GROUP_ID.equals(requirementGroup.getOcds().getId()) ||
                  requirementGroup.getOcds().getId().startsWith(COP_GROUP_ID + ".")) {
                      if (addGroupOrderFieldsForThisGroup(copGroupNamesListForStage, requirements)) {
                          updated = true;
                      }
              }

              if (AWARD_CRITERIA_GROUP_ID.equals(requirementGroup.getOcds().getId()) ||
                  requirementGroup.getOcds().getId().startsWith(AWARD_CRITERIA_GROUP_ID + ".")) {
                      if (addGroupOrderFieldsForThisGroup(awardCriteriaGroupNamesListForStage, requirements)) {
                          updated = true;
                      }
              }

              //
              // once we have added groupOrder fields for all of the defined question groups,
              // we need to set any remaining ones to zero, as required by the bid-pack logic
              //
              if (addDefaultGroupOrderFieldsForThisGroup(requirements)) {
                  updated = true;
              }
          }
      }

      return updated;
  }

  private boolean addGroupOrderFieldsForThisGroup(final List<String> groupNamesList, final Set<Requirement> requirements) {
      boolean updated = false;

      if (null == groupNamesList || groupNamesList.isEmpty()) {
          return updated;
      }

      int groupOrder = 1;

      //
      // add groupOrder fields for all defined question groups
      //

      for (final String groupNameToFind: groupNamesList) {
          for (final Requirement requirement : requirements) {
              final String title = requirement.getOcds().getTitle();

              if (!"Select group name".equalsIgnoreCase(title)) {
                  continue;
              }

              final List<Option> options = requirement.getNonOCDS().getOptions();

              if (null == options || options.isEmpty()) {
                  continue;
              }

              String onlyOptionValue = null;
              int optionValueCount = 0;

              for (final Option option : options) {
                  final Boolean selected = option.getSelect();
                  String value = option.getValue();

                  if (Boolean.TRUE.equals(selected) && null != value && !value.isBlank()) {
                      value = value.trim();
                  }

                  if (null != value && !value.isBlank()) {
                      onlyOptionValue = value.trim();
                      optionValueCount++;
                  }
              }

              final String thisGroupNameValue = optionValueCount == 1 ? onlyOptionValue : null;

              if (null == thisGroupNameValue || !thisGroupNameValue.equals(groupNameToFind)) {
                  continue;
              }

              requirement.getNonOCDS().setGroupOrder(groupOrder);
              updated = true;
          }

          groupOrder++;
      }

      return updated;
  }

  private boolean addDefaultGroupOrderFieldsForThisGroup(final Set<Requirement> requirements) {
      //
      // once we have added groupOrder fields for all of the defined question groups,
      // we need to set any remaining ones to zero, as required by the bid-pack logic
      //

      boolean updated = false;

      for (final Requirement requirement : requirements) {
          final String title = requirement.getOcds().getTitle();

          if (!"Select group name".equalsIgnoreCase(title)) {
              continue;    // we do not have the correct block
          }

          final Integer groupOrder = requirement.getNonOCDS().getGroupOrder();

          if (null != groupOrder) {
              continue;    // we have already added a groupOrder field
          }

          // we have not previously added a groupOrder field, so create a new entry and set the value to zero
          requirement.getNonOCDS().setGroupOrder(DEFAULT_GROUP_ORDER);
          updated = true;
      }

      return updated;
  }

  private List<String> getQuestionGroupsForGroupTypeAndStageNumber(final Integer projectId, final String eventId, final String principal, final String groupType, final Integer stageNumber) {
      final QandAWithProjectDetails response = questionAndAnswerService.getQuestionAndAnswerByEvent(projectId, eventId, principal);

      if (null == response || null == response.getQandA() || response.getQandA().isEmpty()) {
          return null;
      }

      final String useQuestionGroupFullPrefix = groupType + "-" + STAGE_NUMBER + "-" + stageNumber + "-" + USE_QUESTION_GROUPS;

      boolean usesQuestionGroups = false;

      for (QandA responseData: response.getQandA()) {
            if (responseData.getQuestion().equals(useQuestionGroupFullPrefix)) {
                if (null != responseData.getAnswer() && !responseData.getAnswer().isBlank()) {
                    usesQuestionGroups = Boolean.valueOf(responseData.getAnswer());
                    break;
                }
            }
      }

      if (!usesQuestionGroups) {
          return null;
      }

      final String questionGroupFullPrefix = groupType + "-" + STAGE_NUMBER + "-" + stageNumber + "-" + QUESTION_GROUP_PREFIX;

      // note we use a map to ensure we can ultimately return the list in numeric order;
      // just reading directly from the DB does not always give the order we need
      Map<Integer, QandA> questionMap = new HashMap<>();

      for (QandA question: response.getQandA()) {
          if (question.getQuestion().startsWith(questionGroupFullPrefix)) {
              if (null != question.getAnswer() && !question.getAnswer().isBlank()) {
                  // extract the numeric index from the question name, such as: award-criteria-question-group-3
                  String index = question.getQuestion().substring(question.getQuestion().lastIndexOf("-") + 1);
                  // then add this question into the map based on this index
                  questionMap.put(Integer.valueOf(index), question);
              }
          }
      }

      final List<String> questionGroups = new ArrayList<>();

      for (int i=0; i < questionMap.size(); i++) {
          QandA question = questionMap.get(i);
          questionGroups.add(question.getAnswer());
      }

      return questionGroups;
  }
}
