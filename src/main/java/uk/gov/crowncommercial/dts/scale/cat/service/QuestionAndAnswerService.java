package uk.gov.crowncommercial.dts.scale.cat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import uk.gov.crowncommercial.dts.scale.cat.clients.QuestionAndAnswerClient;
import uk.gov.crowncommercial.dts.scale.cat.exception.AuthorisationFailureException;
import uk.gov.crowncommercial.dts.scale.cat.exception.QuestionAndAnswerServiceApplicationException;
import uk.gov.crowncommercial.dts.scale.cat.exception.ResourceNotFoundException;
import uk.gov.crowncommercial.dts.scale.cat.model.agreements.DataTemplate;
import uk.gov.crowncommercial.dts.scale.cat.model.cas.generated.QuestionWrite;
import uk.gov.crowncommercial.dts.scale.cat.model.cas.generated.QuestionWriteResponse;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.QuestionAndAnswer;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.Timestamps;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.QandA;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.QandAWithProjectDetails;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.ViewEventType;
import uk.gov.crowncommercial.dts.scale.cat.repo.QuestionAndAnswerRepo;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 *
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QuestionAndAnswerService {

  @Value("${config.external.questionAndAnswerService.apiKey}")
  private String serviceApiKey;

  private final ValidationService validationService;
  private final UserProfileService userService;
  private final QuestionAndAnswerRepo questionAndAnswerRepo;
  private final ConclaveService conclaveService;
  private final JaggaerService jaggaerService;
  private final RetryableTendersDBDelegate retryableTendersDBDelegate;
  private final AgreementsService agreementsService;
  private final QuestionAndAnswerClient questionAndAnswerClient;
  public static final String JAGGAER_USER_NOT_FOUND = "Jaggaer user not found";
  public static final String Q_AND_A_NOT_FOUND = "QuestionAndAnswer not found by this id %s";

  public QandA createOrUpdateQuestionAndAnswer(final String profile, final Integer projectId,
      final String eventId, final QandA qAndA, final Integer qaId) {
    var procurementEvent = validationService.validateProjectAndEventIds(projectId, eventId);
    var user = userService.resolveBuyerUserProfile(profile)
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

  public QandAWithProjectDetails getQuestionAndAnswerByEvent(final Integer projectId,
      final String eventId, final String principal) {

    // check the roles of supplier
    var user = conclaveService.getUserProfile(principal);
    var procurementEvent = validationService.validateProjectAndEventIds(projectId, eventId);
    var conclaveOrg = conclaveService.getOrganisationIdentity(user.get().getOrganisationId());

    boolean isSupplier = false;
    
    // check the supplier is in org mapping with conclave userid or duns number
    var buyer = user.get().getDetail().getRolePermissionInfo().stream()
        .anyMatch(conclaveUser -> conclaveUser.getRoleKey().equals("JAEGGER_BUYER"));
    if (!buyer) {
      // check the supplier is in org mapping with conclave userid or duns number
      isSupplier = user.get().getDetail().getRolePermissionInfo().stream()
          .anyMatch(conclaveUser -> conclaveUser.getRoleKey().equals("JAEGGER_SUPPLIER"));
    }

    if (isSupplier) {
      // get all suppliers for this event in jaggaer
      var jaggaerSuppliers = jaggaerService.getRfxWithSuppliers(procurementEvent.getExternalEventId())
          .getSuppliersList().getSupplier();
      
      var identifier = conclaveOrg.get().getIdentifier();

      // As per PPG all scheme names are using US-DUN
      var scheme =
          identifier.getScheme().equalsIgnoreCase("US-DUN") ? "US-DUNS" : identifier.getScheme();
      var conclaveOrgId = scheme + "-" + identifier.getId();
      
      // find supplier org-mapping
      var supplierOrgMapping = retryableTendersDBDelegate
          .findOrganisationMappingByOrganisationId(conclaveOrgId);
      if (supplierOrgMapping.isEmpty()) {
        var errorDesc = String.format("No supplier organisation mappings found in Tenders DB %s",
            conclaveOrgId);
        log.error(errorDesc);
        throw new ResourceNotFoundException(errorDesc);
      }

      // Validate supplier
      boolean validSupplier = jaggaerSuppliers.stream().anyMatch(js -> js.getCompanyData().getId()
          .equals(supplierOrgMapping.get().getExternalOrganisationId()));
      if (!validSupplier) {
        throw new AuthorisationFailureException("User not authoried to access this resource");
      }
    }

    var covertedQandAList =
        covertQandAList(questionAndAnswerRepo.findByEventId(procurementEvent.getId()));
    var agreementNo = procurementEvent.getProject().getCaNumber();
    var agreementDetails = agreementsService.getAgreementDetails(agreementNo);
    var lotDetails = agreementsService.getLotDetails(agreementNo, procurementEvent.getProject().getLotNumber());
    var response = new QandAWithProjectDetails().agreementId(agreementNo)
        .projectId(procurementEvent.getProject().getId())
        .projectName(procurementEvent.getProject().getProjectName())
        .agreementName(agreementDetails.getName())
        .lotId(procurementEvent.getProject().getLotNumber())
        .lotName(lotDetails.getName());
    response.setQandA(covertedQandAList);
    return response;
  }
  
  //CAS-1066
  public QandAWithProjectDetails getQuestionAndAnswerForSupplierByEvent(final Integer projectId,
      final String eventId) {
    //Validate event
    var procurementEvent = validationService.validateProjectAndEventIds(projectId, eventId);
    var covertedQandAList =
        covertQandAList(questionAndAnswerRepo.findByEventId(procurementEvent.getId()));
    var agreementNo = procurementEvent.getProject().getCaNumber();
    var agreementDetails = agreementsService.getAgreementDetails(agreementNo);
    var lotDetails =
        agreementsService.getLotDetails(agreementNo, procurementEvent.getProject().getLotNumber());
    var response = new QandAWithProjectDetails().agreementId(agreementNo)
        .projectId(procurementEvent.getProject().getId())
        .projectName(procurementEvent.getProject().getProjectName())
        .agreementName(agreementDetails.getName())
        .lotId(procurementEvent.getProject().getLotNumber()).lotName(lotDetails.getName());
    response.setQandA(covertedQandAList);
    return response;
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

  public boolean createQuestion(String eventType, String eventId, String agreementId, String lotId) {

    String exceptionFormat = "Unexpected error on event creation " + eventType + " template from QAS for Lot " + lotId + " and Agreement " + agreementId
            + " and eventId " + eventId;

    if(StringUtils.isEmpty(eventType)
      || StringUtils.isEmpty(eventId)
      || StringUtils.isEmpty(agreementId)
      || StringUtils.isEmpty(lotId)) {
      throw new QuestionAndAnswerServiceApplicationException(exceptionFormat);
    }

    QuestionWrite questionWrite = new QuestionWrite()
            .eventId(eventId)
            .agreementId(agreementId)
            .lotId(lotId);
      try {
        QuestionWriteResponse questionWriteResponse =  questionAndAnswerClient.createQuestions(questionWrite, eventType, serviceApiKey);
        if(StringUtils.isNotEmpty(questionWriteResponse.getEventId())
          && StringUtils.isNotEmpty(questionWriteResponse.getAgreementId())
          && StringUtils.isNotEmpty(questionWriteResponse.getLotId())) {
            return true;
        }
      } catch(Exception e) {
        log.error("error: ", e);
        throw new QuestionAndAnswerServiceApplicationException(exceptionFormat);
      }

      return false;
  }

  /**
   * Get the Event Type Data Templates for a given Lot for a given Agreement
   */
  @Cacheable(value = "qAndACache", key = "#root.methodName + '-' + #agreementId + '-' + #lotId + '-' + #eventType.value")
  public List<DataTemplate> getLotEventTypeDataTemplates(final String agreementId, final String lotId, final ViewEventType eventType) {


    // Call the Question and answer Service to request the data templates for the given agreement, lot and event type, first formatting the lot ID
    String formattedLotId = lotId.replace("Lot ", "");
    String exceptionFormat = "Unexpected error retrieving " + eventType.name() + " template from AS for Lot " + lotId + " and Agreement " + agreementId;

    try {
      List<DataTemplate> model = questionAndAnswerClient.getEventDataTemplates(agreementId, formattedLotId, eventType.getValue(), serviceApiKey);

      // Return the model if possible, otherwise throw an error
      if (model != null) {
        return model;
      } else {
        log.warn("Empty response for Data Templates from Question and answer Service for " + agreementId + ", lot " + formattedLotId + ", event type " + eventType.name());
        throw new QuestionAndAnswerServiceApplicationException(exceptionFormat);
      }
    } catch (Exception ex) {
      log.error("Error getting Data Templates from Question and answer Service for " + agreementId + ", lot " + formattedLotId + ", event type " + eventType.name(), ex);
      throw new QuestionAndAnswerServiceApplicationException(exceptionFormat);
    }
  }
}
