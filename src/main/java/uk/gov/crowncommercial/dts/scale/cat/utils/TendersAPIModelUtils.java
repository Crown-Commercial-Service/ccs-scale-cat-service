package uk.gov.crowncommercial.dts.scale.cat.utils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uk.gov.crowncommercial.dts.scale.cat.config.ApplicationFlagsConfig;
import uk.gov.crowncommercial.dts.scale.cat.config.Constants;
import uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.model.ApiError;
import uk.gov.crowncommercial.dts.scale.cat.model.DocumentKey;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.DocumentUpload;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.*;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.Attachment;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.RfxSetting;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Utility methods for building and manipulating the sources generated from the Tenders API
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TendersAPIModelUtils {

  private static final List<String> EVALUATING_STATUS_LIST = List.of("Qualification Evaluation","Technical Evaluation",
          "Commercial Evaluation","Best and Final Offer Evaluation","TIOC Closed");
  private static final List<String> PRE_AWARD_STATUS_LIST= List.of("Final Evaluation - Pre-Awarded","Awarding Approval");
  private static final List<String> AWARD_STATUS_LIST= List.of("Awarded","Mixed Awarding");
  private static final String TO_BE_EVALUATED_STATUS = "To Be Evaluated";
  private static final String EVALUATED_STATUS = "Final Evaluation";
  private static final String CLOSED_STATUS = "CLOSED";
  private static final String COMPLETE_STATUS = "COMPLETE";

  private final JaggaerAPIConfig jaggaerAPIConfig;
  private final ApplicationFlagsConfig appFlagsConfig;

  public DraftProcurementProject buildDraftProcurementProject(
      final AgreementDetails agreementDetails, final Integer procurementID, final String eventID,
      final String projectTitle, final String conclaveOrgId) {
    var draftProcurementProject = new DraftProcurementProject();
    draftProcurementProject.setProcurementID(procurementID);
    draftProcurementProject.setEventId(eventID);

    var defaultNameComponents = new DefaultNameComponents();
    defaultNameComponents.setAgreementId(agreementDetails.getAgreementId());
    defaultNameComponents.setLotId(agreementDetails.getLotId());
    defaultNameComponents.setOrg(conclaveOrgId);

    var defaultName = new DefaultName();
    defaultName.setName(projectTitle);
    defaultName.setComponents(defaultNameComponents);
    draftProcurementProject.setDefaultName(defaultName);

    return draftProcurementProject;
  }

  public EventSummary buildEventSummary(final String eventId, final String name,
      final Optional<String> supportID, final ViewEventType type, final TenderStatus status,
      final ReleaseTag stage, final Optional<Integer> assessmentId) {
    var eventSummary = new EventSummary();
    eventSummary.setId(eventId);
    eventSummary.setTitle(name);
    eventSummary.setEventStage(stage);
    eventSummary.setStatus(status);
    eventSummary.setEventType(type);

    supportID.ifPresent(eventSummary::setEventSupportId);
    assessmentId.ifPresent(eventSummary::setAssessmentId);

    return eventSummary;
  }

  public Errors buildDefaultErrors(final String status, final String title, final String details) {
    var apiError = new ApiError(status, title,
        appFlagsConfig.getDevMode() != null && appFlagsConfig.getDevMode() ? details : "");
    return buildErrors(Arrays.asList(apiError));
  }

  public Errors buildErrors(final List<ApiError> apiErrors) {
    var errors = new Errors();
    errors.setErrors(apiErrors);
    return errors;
  }

  public EventDetail buildEventDetail(final RfxSetting rfxSetting,
      final ProcurementEvent procurementEvent, final Collection<EvalCriteria> buyerQuestions) {
    var eventDetail = new EventDetail();

    var agreementDetails = new AgreementDetails();
    agreementDetails.setAgreementId(procurementEvent.getProject().getCaNumber());
    agreementDetails.setLotId(procurementEvent.getProject().getLotNumber());

    // TODO: SCC-439 complete mapping of all nonOCDS and OCDS fields

    // Non-OCDS
    var eventDetailNonOCDS = new EventDetailNonOCDS();
    eventDetailNonOCDS.setEventType(ViewEventType.fromValue(procurementEvent.getEventType()));
    eventDetailNonOCDS.setEventSupportId(procurementEvent.getExternalReferenceId());
    eventDetailNonOCDS.setAssessmentId(procurementEvent.getAssessmentId());
    eventDetailNonOCDS.setAssessmentSupplierTarget(procurementEvent.getAssessmentSupplierTarget());
    if (buyerQuestions != null && !buyerQuestions.isEmpty()) {
      eventDetailNonOCDS.setBuyerQuestions(List.copyOf(buyerQuestions));
    }
    eventDetailNonOCDS.setDashboardStatus(getDashboardStatus(rfxSetting,procurementEvent));
    eventDetail.setNonOCDS(eventDetailNonOCDS);


    // OCDS
    var eventDetailOCDS = new EventDetailOCDS();
    eventDetailOCDS.setId(rfxSetting.getRfxId());
    eventDetailOCDS.setTitle(rfxSetting.getShortDescription());
    eventDetailOCDS.setDescription(rfxSetting.getLongDescription());
    eventDetailOCDS
        .setStatus(jaggaerAPIConfig.getRfxStatusToTenderStatus().get(rfxSetting.getStatusCode()));
    // TODO: TBC - mappings required
    eventDetailOCDS.setAwardCriteria(AwardCriteria.RATEDCRITERIA);
    eventDetail.setOCDS(eventDetailOCDS);

    return eventDetail;
  }

  /*
   * SCAT-4788 - need to call this to populate EventSummary.dashboardStatus and
   * EventDetail.nonOCDS.dashboardStatus
   */
  public DashboardStatus getDashboardStatus(
      final RfxSetting rfxSetting, final ProcurementEvent procurementEvent) {

    var tenderStatus = procurementEvent.getTenderStatus();
    if (Objects.nonNull(tenderStatus)) {
      if (Objects.equals(COMPLETE_STATUS, tenderStatus)) {
        return DashboardStatus.COMPLETE;
      } else if (Objects.equals(CLOSED_STATUS, tenderStatus)) {
        return DashboardStatus.CLOSED;
      } else // No rfx, use procurement event status
      if (Constants.TENDER_DB_ONLY_EVENT_TYPES.contains(
          DefineEventType.fromValue(procurementEvent.getEventType()))) {
        return DashboardStatus.ASSESSMENT;
      } else // TODO: Event types: EOI,RFI,FC OR DA etc
      if (Constants.TENDER_NON_DB_EVENT_TYPES.contains(
              DefineEventType.fromValue(procurementEvent.getEventType()))
          && Objects.nonNull(rfxSetting)) {

        if (Objects.isNull(rfxSetting.getPublishDate())) {
          return DashboardStatus.IN_PROGRESS;
        } else if (Objects.nonNull(rfxSetting.getCloseDate())
            && rfxSetting.getCloseDate().isAfter(OffsetDateTime.now())) {
          return DashboardStatus.PUBLISHED;
        } else {
          return evaluateDashboardStatusFromRfxSettingStatus(rfxSetting);
        }

      }
    }
    log.error("DashboardStatus is not determined , returning UNKNOWN Status ");
    return DashboardStatus.UNKNOWN;
  }

  private DashboardStatus evaluateDashboardStatusFromRfxSettingStatus(RfxSetting rfxSetting) {
      if (rfxSetting.getStatus().equals(TO_BE_EVALUATED_STATUS)) {
        return DashboardStatus.TO_BE_EVALUATED;
      } else {
        if (EVALUATING_STATUS_LIST.contains(rfxSetting.getStatus())) {
          return DashboardStatus.EVALUATING;
        } else if(EVALUATED_STATUS.equals(rfxSetting.getStatus())) {
            return DashboardStatus.EVALUATED;
        } else {
            if(PRE_AWARD_STATUS_LIST.contains(rfxSetting.getStatus())){
              return DashboardStatus.PRE_AWARD;
            }else if(AWARD_STATUS_LIST.contains(rfxSetting.getStatus())) {
              return DashboardStatus.AWARDED;
            }
        }
    }
    log.error("DashboardStatus is not determined , returning UNKNOWN Status ");
    return DashboardStatus.UNKNOWN;
  }

  public DocumentSummary buildDocumentSummary(final Attachment attachment,
      final DocumentAudienceType audienceType) {

    var key = new DocumentKey(Integer.valueOf(attachment.getFileId()), attachment.getFileName(),
        audienceType);
    var doc = new DocumentSummary();
    doc.setId(key.getDocumentId());
    doc.setFileName(attachment.getFileName());
    doc.setFileSize(attachment.getFileSize());
    doc.setDescription(attachment.getFileDescription());
    doc.setAudience(audienceType);
    return doc;
  }

  public DocumentSummary buildDocumentSummary(final DocumentUpload documentUpload) {
    var docKey = DocumentKey.fromString(documentUpload.getDocumentId());

    return new DocumentSummary().id(documentUpload.getDocumentId()).fileName(docKey.getFileName())
        .fileSize(documentUpload.getSize()).description(documentUpload.getDocumentDescription())
        .audience(documentUpload.getAudience());
  }
}
