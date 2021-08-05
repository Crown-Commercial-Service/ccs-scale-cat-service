package uk.gov.crowncommercial.dts.scale.cat.utils;

import java.util.List;
import org.springframework.stereotype.Component;
import uk.gov.crowncommercial.dts.scale.cat.model.ApiError;
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
    defaultNameComponents.setAgreementID(agreementDetails.getAgreementId());
    defaultNameComponents.setLotID(agreementDetails.getLotId());
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

}
