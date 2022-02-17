package uk.gov.crowncommercial.dts.scale.cat.utils;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import uk.gov.crowncommercial.dts.scale.cat.config.ApplicationFlagsConfig;
import uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.model.ApiError;
import uk.gov.crowncommercial.dts.scale.cat.model.DocumentKey;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.*;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.Attachment;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.RfxSetting;

/**
 * Utility methods for building and manipulating the sources generated from the Tenders API
 */
@Component
@RequiredArgsConstructor
public class TendersAPIModelUtils {

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

}
