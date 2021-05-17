package uk.gov.crowncommercial.dts.scale.cat.utils;

import org.springframework.stereotype.Component;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.AgreementDetails;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.DefaultName;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.DefaultNameComponents;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.DraftProcurementProject;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.EventSummary;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.EventType;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.TenderStatus;

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

  public EventSummary buildEventSummary(String id, String name, String supportID, EventType type,
      TenderStatus status, String stage) {
    var eventSummary = new EventSummary();
    eventSummary.setEventID(id);
    eventSummary.setName(name);
    eventSummary.setEventSupportID(supportID);
    eventSummary.setEventType(type);
    eventSummary.setStatus(status);
    eventSummary.setEventStage(stage);

    return eventSummary;
  }

}
