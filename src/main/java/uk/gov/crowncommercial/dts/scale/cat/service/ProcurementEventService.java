package uk.gov.crowncommercial.dts.scale.cat.service;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.config.OcdsConfig;
import uk.gov.crowncommercial.dts.scale.cat.exception.JaggaerApplicationException;
import uk.gov.crowncommercial.dts.scale.cat.exception.ResourceNotFoundException;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementProject;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.EventSummary;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.EventType;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.Tender;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.TenderStatus;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.*;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;
import uk.gov.crowncommercial.dts.scale.cat.utils.TendersAPIModelUtils;

/**
 *
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProcurementEventService {

  // TODO: Rfx type is currently fixed, pending further design from Nick to add to request object(s)
  private static final EventType TEMP_EVENT_TYPE = EventType.RFP;

  // TODO: Jaggaer suppliers hard coded for now, pending further design
  private static final List<String> TEMP_SUPPLIER_IDS = Arrays.asList("53104", "53410", "53411");

  // TODO: BuyerID hard coded for now
  private static final String TEMP_BUYER_ID = "51435";

  private static final Integer RFI_FLAG = 0;
  private static final String RFX_TYPE = "STANDARD_ITT";
  private static final String ADDITIONAL_INFO_FRAMEWORK_NAME = "Framework Name";
  private static final String ADDITIONAL_INFO_LOT_NUMBER = "Lot Number";
  private static final String ADDITIONAL_INFO_LOCALE = "en_GB";
  private static final String EVENT_STAGE = "Tender";

  private final UserProfileService userProfileService;
  private final JaggaerAPIConfig jaggaerAPIConfig;
  private final OcdsConfig ocdsConfig;
  private final WebClient jaggaerWebClient;
  private final RetryableTendersDBDelegate retryableTendersDBDelegate;
  private final TendersAPIModelUtils tendersAPIModelUtils;

  /**
   * This will create a Jaggaer Rfx (CCS 'Event' equivalent) based on agreement details. Designed to
   * be called directly from the {@link ProcurementProjectService} during the Jaggaer project
   * creation process.
   *
   * @param projectId CCS project id
   * @param principal
   * @return
   */
  public EventSummary createFromProject(Integer projectId, String principal) {

    // Get project from tenders DB to obtain Jaggaer project id
    ProcurementProject project = retryableTendersDBDelegate.findProcurementProjectById(projectId)
        .orElseThrow(() -> new ResourceNotFoundException("Project '" + projectId + "' not found"));

    var createUpdateRfx = createUpdateRfxRequest(project, principal);

    tendersAPIModelUtils.prettyPrintJson(createUpdateRfx);

    CreateUpdateRfxResponse createRfxResponse =
        jaggaerWebClient.post().uri(jaggaerAPIConfig.getCreateEvent().get("endpoint"))
            .bodyValue(createUpdateRfx).retrieve().bodyToMono(CreateUpdateRfxResponse.class)
            .block(Duration.ofSeconds(jaggaerAPIConfig.getTimeoutDuration()));

    if (createRfxResponse == null) {
      throw new JaggaerApplicationException(HttpStatus.INTERNAL_SERVER_ERROR.value(),
          "Unexpected error creating Rfx");
    }

    if (createRfxResponse.getReturnCode() != 0
        || !createRfxResponse.getReturnMessage().equals("OK")) {
      log.error(createRfxResponse.toString());
      throw new JaggaerApplicationException(createRfxResponse.getReturnCode(),
          createRfxResponse.getReturnMessage());
    }
    log.info("Created event: {}", createRfxResponse);

    tendersAPIModelUtils.prettyPrintJson(createRfxResponse);

    // Persist the Jaggaer Rfx details as a new event in the tenders DB
    String ocdsAuthority = ocdsConfig.getAuthority();
    String ocidPrefix = ocdsConfig.getOcidPrefix();
    String eventName = createUpdateRfx.getRfx().getRfxSetting().getShortDescription();

    var procurementEvent = retryableTendersDBDelegate.save(ProcurementEvent.of(project, eventName,
        createRfxResponse.getRfxReferenceCode(), ocdsAuthority, ocidPrefix, principal));

    return tendersAPIModelUtils.buildEventSummary(procurementEvent.getEventID(), eventName,
        createRfxResponse.getRfxReferenceCode(), TEMP_EVENT_TYPE, TenderStatus.PLANNING,
        EVENT_STAGE);
  }

  /**
   * This endpoint will create a Jaggaer Rfx (CCS 'Event' equivalent) on an existing project.
   *
   * @param projectId CCS project id
   * @param tender CCS tender object (only relevant values need to be populated)
   * @param principal
   * @return
   */
  public EventSummary createFromTender(final Integer projectId, final Tender tender,
      final String principal) {
    // Use of Tender is redundant as none of the data is currently required (may change)
    return createFromProject(projectId, principal);
  }

  /**
   * Create Jaggaer request object.
   */
  private CreateUpdateRfx createUpdateRfxRequest(final ProcurementProject project,
      final String principal) {

    // Fetch Jaggaer ID (and org?) from Jaggaer profile based on OIDC login id
    String jaggaerUserId = userProfileService.resolveJaggaerUserId(principal);

    var eventName = getDefaultEventTitle(project.getProjectName(), TEMP_EVENT_TYPE.getValue());
    var buyerCompany = new BuyerCompany(TEMP_BUYER_ID);
    var ownerUser = new OwnerUser(jaggaerUserId);

    var rfxSetting =
        RfxSetting.builder().rfiFlag(RFI_FLAG).tenderReferenceCode(project.getJaggaerProjectId())
            .templateReferenceCode(jaggaerAPIConfig.getCreateEvent().get("templateId"))
            .shortDescription(eventName).buyerCompany(buyerCompany).ownerUser(ownerUser)
            .rfxType(RFX_TYPE).build();

    var additionalInfoFramework = AdditionalInfo.builder().name(ADDITIONAL_INFO_FRAMEWORK_NAME)
        .label(ADDITIONAL_INFO_FRAMEWORK_NAME).labelLocale(ADDITIONAL_INFO_LOCALE)
        .values(
            new AdditionalInfoValues(Arrays.asList(new AdditionalInfoValue(project.getCaNumber()))))
        .build();

    var additionalInfoLot =
        AdditionalInfo.builder().name(ADDITIONAL_INFO_LOT_NUMBER).label(ADDITIONAL_INFO_LOT_NUMBER)
            .labelLocale(ADDITIONAL_INFO_LOCALE).values(new AdditionalInfoValues(
                Arrays.asList(new AdditionalInfoValue(project.getLotNumber()))))
            .build();

    var rfxAdditionalInfoList =
        new RfxAdditionalInfoList(Arrays.asList(additionalInfoFramework, additionalInfoLot));

    var suppliersList = new SuppliersList(getSuppliers());
    var rfx = new Rfx(rfxSetting, rfxAdditionalInfoList, suppliersList);

    return new CreateUpdateRfx(OperationCode.CREATE_FROM_TEMPLATE, rfx);
  }

  /**
   * 
   * @param projectId
   * @param eventId
   * @param name
   * @param principal
   * @return
   */
  public EventSummary updateProcurementEventName(Integer projectId, Integer eventId, String name,
      String principal) {

    // Get project from tenders DB to obtain Jaggaer project id
    ProcurementEvent event = retryableTendersDBDelegate.findProcurementEventById(eventId)
        .orElseThrow(() -> new ResourceNotFoundException("Event '" + eventId + "' not found"));


    // Fetch Jaggaer ID (and org?) from Jaggaer profile based on OIDC login id
    String jaggaerUserId = userProfileService.resolveJaggaerUserId(principal);

    // TODO: need a new col in DB (with Trev)
    var rfxId = "rfq_53896";
    var rfxReferenceCode = "itt_1949";

    var rfxSetting = RfxSetting.builder().rfxId(rfxId).rfxReferenceCode(rfxReferenceCode)
        .shortDescription(name).build();

    var rfx = new Rfx(rfxSetting, null, null);

    CreateUpdateRfxResponse createRfxResponse =
        jaggaerWebClient.post().uri(jaggaerAPIConfig.getCreateEvent().get("endpoint"))
            .bodyValue(new CreateUpdateRfx(OperationCode.UPDATE, rfx)).retrieve()
            .bodyToMono(CreateUpdateRfxResponse.class)
            .block(Duration.ofSeconds(jaggaerAPIConfig.getTimeoutDuration()));

    // TODO - response in API says it is "OK", but content type is "application/vnd.api+json" - is a
    // simple "OK" string valid?
    return null;
  }

  /**
   * TODO: Placeholder for retrieving suppliers - ultimately needs to come from Agreements Service
   * (pending further analysis/design from Nick).
   */
  private List<Supplier> getSuppliers() {
    return TEMP_SUPPLIER_IDS.stream().map(id -> new Supplier(new CompanyData(id)))
        .collect(Collectors.toList());
  }

  private String getDefaultEventTitle(String projectName, String eventType) {
    return String.format(jaggaerAPIConfig.getCreateEvent().get("defaultTitleFormat"), projectName,
        eventType);
  }

}
