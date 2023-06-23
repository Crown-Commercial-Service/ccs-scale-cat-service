package uk.gov.crowncommercial.dts.scale.cat.utils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uk.gov.crowncommercial.dts.scale.cat.config.ApplicationFlagsConfig;
import uk.gov.crowncommercial.dts.scale.cat.config.Constants;
import uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.model.ApiError;
import uk.gov.crowncommercial.dts.scale.cat.model.DocumentKey;
import uk.gov.crowncommercial.dts.scale.cat.model.SchemeType;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.AsyncPublishedStatus;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.DocumentUpload;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.*;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.Attachment;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.RfxSetting;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;
import static uk.gov.crowncommercial.dts.scale.cat.config.Constants.*;

/**
 * Utility methods for building and manipulating the sources generated from the Tenders API
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TendersAPIModelUtils {

  private static final List<String> EVALUATING_STATUS_LIST = List.of("Qualification Evaluation","Technical Evaluation",
          "Commercial Evaluation","Best and Final Offer Evaluation","TIOC Closed");
  private static final List<String> PRE_AWARD_STATUS_LIST= List.of("Final Evaluation - Pre-Awarded","Awarding Approval", "Final Evaluation - Saved");
  private static final List<String> AWARD_STATUS_LIST= List.of("Awarded","Mixed Awarding");


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

    if(null != procurementEvent.getTemplateId())
      eventDetailNonOCDS.setTemplateGroupId(BigDecimal.valueOf(procurementEvent.getTemplateId()));

    // OCDS
    var eventDetailOCDS = new EventDetailOCDS();
    // TODO : Verify with Nick whats the functionality from RFI(=) to FCA(=)
    if (Objects.nonNull(rfxSetting)) {
      eventDetailOCDS.setId(rfxSetting.getRfxId());
      eventDetailOCDS.setTitle(rfxSetting.getShortDescription());
      eventDetailOCDS.setDescription(rfxSetting.getLongDescription());
      eventDetailOCDS.setStatus(
          jaggaerAPIConfig.getRfxStatusToTenderStatus().get(rfxSetting.getStatusCode()));
      // TODO: TBC - mappings required

    }
    eventDetailOCDS.setAwardCriteria(AwardCriteria.RATEDCRITERIA);
    eventDetail.setOCDS(eventDetailOCDS);

    return eventDetail;
  }

  /*
   * SCAT-4788 - need to call this to populate EventSummary.dashboardStatus and
   * EventDetail.nonOCDS.dashboardStatus
   */
  public static DashboardStatus getDashboardStatus(
      final RfxSetting rfxSetting, final ProcurementEvent procurementEvent) {

    var tenderStatus = procurementEvent.getTenderStatus();
    var dashboardStatusFromTenderStatus =
        deriveDashboardStatusBasedOnTenderStatus(tenderStatus);

    if (null != dashboardStatusFromTenderStatus) {
      return dashboardStatusFromTenderStatus;
    } else { // No rfx, use procurement event status
      if (null!=procurementEvent.getEventType()
          && Constants.TENDER_DB_ONLY_EVENT_TYPES.contains(
              ViewEventType.fromValue(procurementEvent.getEventType()))) {
        return DashboardStatus.ASSESSMENT;
      } else // TODO: Event types: EOI,RFI,FC OR DA etc
      if (null!=procurementEvent.getEventType()
          && Constants.TENDER_NON_DB_EVENT_TYPES.contains(
              ViewEventType.fromValue(procurementEvent.getEventType()))
          && Objects.nonNull(rfxSetting)) {
        if (Objects.isNull(rfxSetting.getPublishDate()) && Objects.isNull(procurementEvent.getAsyncPublishedStatus())) {
          return DashboardStatus.IN_PROGRESS;
        } else {
          return deriveAsyncPublishStatus(rfxSetting, procurementEvent);
        }
      }
    }
    log.debug("DashboardStatus is not determined , returning UNKNOWN Status ");
    return DashboardStatus.UNKNOWN;
  }

  private static DashboardStatus deriveAsyncPublishStatus(final RfxSetting rfxSetting, final ProcurementEvent procurementEvent) {
    if (isAsyncPublishing(procurementEvent)) {
      return DashboardStatus.PUBLISHING;
    }
    if (isAsyncPublishFailed(procurementEvent)) {
      return DashboardStatus.PUBLISH_FAILED;
    }
    if (isPublished(rfxSetting)) {
      return DashboardStatus.PUBLISHED;
    }
    return evaluateDashboardStatusFromRfxSettingStatus(rfxSetting);
  }

  private static boolean isAsyncPublishing(ProcurementEvent procurementEvent) {
    return Objects.nonNull(procurementEvent.getAsyncPublishedStatus()) && ASYNC_PUBLISH_STATUS_TYPES
        .contains(AsyncPublishedStatus.valueOf(procurementEvent.getAsyncPublishedStatus()));
  }

  private static boolean isAsyncPublishFailed(ProcurementEvent procurementEvent) {
    return Objects.nonNull(procurementEvent.getAsyncPublishedStatus())
        && AsyncPublishedStatus.FAILED.name()
            .equalsIgnoreCase(procurementEvent.getAsyncPublishedStatus());
  }

  private static boolean isPublished(RfxSetting rfxSetting) {
    return Objects.nonNull(rfxSetting.getCloseDate())
        && rfxSetting.getCloseDate().isAfter(OffsetDateTime.now());
  }


  private static DashboardStatus deriveDashboardStatusBasedOnTenderStatus(String tenderStatus) {

    if (Objects.nonNull(tenderStatus)) {
      if (tenderStatus.strip().equalsIgnoreCase(COMPLETE_STATUS)) {
        return DashboardStatus.COMPLETE;
      } else if (CLOSED_STATUS_LIST.contains(tenderStatus.strip().toLowerCase())) {
        return DashboardStatus.CLOSED;
      }
    }
    return null;
  }

  public static DashboardStatus evaluateDashboardStatusFromRfxSettingStatus(RfxSetting rfxSetting) {
    if (rfxSetting.getStatus().strip().equalsIgnoreCase(TO_BE_EVALUATED_STATUS)) {
      return DashboardStatus.TO_BE_EVALUATED;
    } else {
      if (EVALUATING_STATUS_LIST.stream()
          .anyMatch(rfxSetting.getStatus().strip()::equalsIgnoreCase)) {
        return DashboardStatus.EVALUATING;
      } else if (EVALUATED_STATUS.equalsIgnoreCase(rfxSetting.getStatus().strip())) {
        return DashboardStatus.EVALUATED;
      } else {
        if (PRE_AWARD_STATUS_LIST.stream()
            .anyMatch(rfxSetting.getStatus().strip()::equalsIgnoreCase)) {
          return DashboardStatus.PRE_AWARD;
        } else if (AWARD_STATUS_LIST.stream()
            .anyMatch(rfxSetting.getStatus().strip()::equalsIgnoreCase)) {
          return DashboardStatus.AWARDED;
        }
      }
    }
    log.debug("DashboardStatus is not determined , returning UNKNOWN Status ");
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

  public static Period1  getTenderPeriod(Instant publishedDate, Instant closedDate) {

    Period1 period1=new Period1();

    OffsetDateTime startDate=null;
    OffsetDateTime endDate=null;
    if(Objects.nonNull(publishedDate)){
      startDate=OffsetDateTime.ofInstant(publishedDate, ZoneId.systemDefault());
    }
    if(Objects.nonNull(closedDate)){
      endDate=OffsetDateTime.ofInstant(closedDate, ZoneId.systemDefault());
    }
    period1.startDate(startDate).endDate(endDate);
    return period1;

  }

  public static Instant getInstantFromDate(OffsetDateTime  offsetDateTime) {
    return null!=offsetDateTime?offsetDateTime.toInstant():null;
  }
  
  public static OffsetDateTime getOffsetDateTimeFromInstant(Instant instant) {
    return null != instant ? OffsetDateTime.ofInstant(instant, ZoneId.systemDefault()) : null;
  }
  
  public static Map<String, SchemeType> validateSchemeType() {
    var schemeMapping = new HashMap<String, SchemeType>();
    schemeMapping.put("US-DUN", SchemeType.DUNS);
    schemeMapping.put("GB-COH", SchemeType.COH);
    schemeMapping.put("VAT", SchemeType.VAT);
    schemeMapping.put("NHS", SchemeType.NHS);
    return schemeMapping;
  }
}
