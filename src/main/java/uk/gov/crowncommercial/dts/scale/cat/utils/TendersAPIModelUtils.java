package uk.gov.crowncommercial.dts.scale.cat.utils;

import java.util.List;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.model.ApiError;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.*;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.RfxSetting;

/**
 * Utility methods for building and manipulating the sources generated from the Tenders API
 */
@Component
@RequiredArgsConstructor
public class TendersAPIModelUtils {

  private final JaggaerAPIConfig jaggaerAPIConfig;

  public DraftProcurementProject buildDraftProcurementProject(AgreementDetails agreementDetails,
      Integer procurementID, String eventID, String projectTitle) {
    var draftProcurementProject = new DraftProcurementProject();
    draftProcurementProject.setPocurementID(procurementID);
    draftProcurementProject.setEventId(eventID);

    var defaultNameComponents = new DefaultNameComponents();
    defaultNameComponents.setAgreementId(agreementDetails.getAgreementId());
    defaultNameComponents.setLotId(agreementDetails.getLotId());
    defaultNameComponents.setOrg("CCS");

    var defaultName = new DefaultName();
    defaultName.setName(projectTitle);
    defaultName.setComponents(defaultNameComponents);
    draftProcurementProject.setDefaultName(defaultName);

    return draftProcurementProject;
  }

  public EventSummary buildEventSummary(String eventId, String name, String supportID,
      EventType type, TenderStatus status, ReleaseTag stage) {
    var eventSummary = new EventSummary();
    eventSummary.setId(eventId);
    eventSummary.setTitle(name);
    eventSummary.setEventStage(stage);
    eventSummary.setStatus(status);
    eventSummary.setEventType(type);
    eventSummary.setEventSupportId(supportID);
    return eventSummary;
  }

  public Errors buildErrors(List<ApiError> apiErrors) {
    var errors = new Errors();
    errors.setErrors(apiErrors);
    return errors;
  }

  public EventDetail buildEventDetail(RfxSetting rfxSetting, ProcurementEvent procurementEvent) {
    var eventDetail = new EventDetail();

    var agreementDetails = new AgreementDetails();
    agreementDetails.setAgreementId(procurementEvent.getProject().getCaNumber());
    agreementDetails.setLotId(procurementEvent.getProject().getLotNumber());

    // TODO: SCC-439 complete mapping of all nonOCDS and OCDS fields

    // Non-OCDS
    var eventDetailNonOCDS = new EventDetailNonOCDS();
    eventDetailNonOCDS.setEventType(EventType.fromValue(procurementEvent.getEventType()));
    eventDetailNonOCDS.setEventSupportId(null);
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

}
