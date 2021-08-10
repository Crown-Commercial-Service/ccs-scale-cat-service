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

  public DraftProcurementProject buildDraftProcurementProject(ProjectRequest projectRequest,
      Integer procurementID, String eventID, String projectTitle) {
    var draftProcurementProject = new DraftProcurementProject();
    draftProcurementProject.setPocurementID(procurementID);
    draftProcurementProject.setEventID(eventID);

    var defaultNameComponents = new DefaultNameComponents();
    defaultNameComponents.setAgreementID(projectRequest.getAgreementId());
    defaultNameComponents.setLotID(projectRequest.getLotId());
    defaultNameComponents.setOrg("CCS");

    var defaultName = new DefaultName();
    defaultName.setName(projectTitle);
    defaultName.setComponents(defaultNameComponents);
    draftProcurementProject.setDefaultName(defaultName);

    return draftProcurementProject;
  }

  public EventStatus buildEventStatus(Integer projectId, String eventId, String name,
      String supportID, EventType type, TenderStatus status, String stage) {
    var eventStatus = new EventStatus();
    eventStatus.setProjectId(projectId);
    eventStatus.setEventId(eventId);
    eventStatus.setEventName(name);
    eventStatus.setEventSupportId(supportID);
    eventStatus.setEventType(type);
    eventStatus.setEventStatus(status);
    eventStatus.setEventStage(stage);

    return eventStatus;
  }

  public Errors buildErrors(List<ApiError> apiErrors) {
    var errors = new Errors();
    errors.setErrors(apiErrors);
    return errors;
  }

  public EventDetail buildEventDetail(RfxSetting rfxSetting, ProcurementEvent procurementEvent) {
    var eventDetail = new EventDetail();

    var projectSummary = new ProjectSummary();
    var agreementDefinition = new AgreementDefinition();
    agreementDefinition.setAgreementId(procurementEvent.getProject().getCaNumber());
    // agreementDefinition.setAgreementName(TODO: Get it from the agreements service);
    agreementDefinition.setLotId(procurementEvent.getProject().getLotNumber());

    // Non-OCDS
    var eventDetailNonOCDS = new EventDetailNonOCDS();
    eventDetailNonOCDS.setEventId(rfxSetting.getRfxId());
    eventDetailNonOCDS.setEventName(rfxSetting.getShortDescription());
    eventDetailNonOCDS.setEventStatus(
        jaggaerAPIConfig.getRfxStatusToTenderStatus().get(rfxSetting.getStatusCode()));
    // eventDetailNonOCDS.setEventResponseDate(rfxSetting.get);
    eventDetail.setNonOCDS(eventDetailNonOCDS);

    // OCDS
    var eventDetailOCDS = new EventDetailOCDS();

    eventDetail.setOCDS(eventDetailOCDS);
    // TODO: Map to SCC-439 nonOCDS and OCDS fields
    // eventDetail.setId(rfxSetting.getRfxId());
    // eventDetail.setTitle(rfxSetting.getShortDescription());
    // eventDetail.setDescription(rfxSetting.getLongDescription());
    // eventDetail.setStatus(jaggaerAPIConfig.getRfxStatusToTenderStatus().get(rfxSetting.getStatusCode()));
    //
    // // TODO: TBC - mappings required
    // eventDetail.setAwardCriteria(AwardCriteria.RATEDCRITERIA);
    return eventDetail;
  }

}
