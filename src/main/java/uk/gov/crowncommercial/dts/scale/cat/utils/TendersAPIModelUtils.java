package uk.gov.crowncommercial.dts.scale.cat.utils;

import org.springframework.stereotype.Component;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.*;

/**
 * Utility methods for building and manipulating the sources generated from the Tenders API
 */
@Component
public class TendersAPIModelUtils {

  public DraftProcurementProject buildDraftProcurementProject(AgreementDetails agreementDetails,
      Integer procurementID, String eventID, String projectTitle) {
    var draftProcurementProject = new DraftProcurementProject();
    draftProcurementProject.setPocurementID(procurementID);
    draftProcurementProject.setEventID(eventID);

    var defaultNameComponents = new DefaultNameComponents();
    defaultNameComponents.setAgreementID(agreementDetails.getAgreementID());
    defaultNameComponents.setLotID(agreementDetails.getLotID());
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
    eventStatus.setProjectID(projectId);
    eventStatus.setEventID(eventId);
    eventStatus.setName(name);
    eventStatus.setEventSupportID(supportID);
    eventStatus.setEventType(type);
    eventStatus.setStatus(status);
    eventStatus.setEventStage(stage);

    return eventStatus;
  }

}
