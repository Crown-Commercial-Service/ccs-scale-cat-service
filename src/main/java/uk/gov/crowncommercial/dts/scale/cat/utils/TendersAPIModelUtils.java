package uk.gov.crowncommercial.dts.scale.cat.utils;

import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig;
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

  public Tender buildTender(RfxSetting rfxSetting) {
    var tender = new Tender();
    tender.setId(rfxSetting.getRfxId());
    tender.setTitle(rfxSetting.getShortDescription());
    tender.setDescription(rfxSetting.getLongDescription());
    tender.setStatus(jaggaerAPIConfig.getRfxStatusToTenderStatus().get(rfxSetting.getStatusCode()));

    // TODO: TBC - mappings required
    tender.setAwardCriteria(AwardCriteria.RATEDCRITERIA);
    return tender;
  }

}
