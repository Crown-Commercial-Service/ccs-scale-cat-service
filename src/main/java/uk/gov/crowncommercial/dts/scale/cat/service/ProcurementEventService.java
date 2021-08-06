package uk.gov.crowncommercial.dts.scale.cat.service;

import static java.time.Duration.ofSeconds;
import static java.util.Optional.ofNullable;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig.ENDPOINT;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.web.reactive.function.client.WebClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.config.Constants;
import uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.config.OcdsConfig;
import uk.gov.crowncommercial.dts.scale.cat.exception.JaggaerApplicationException;
import uk.gov.crowncommercial.dts.scale.cat.exception.ResourceNotFoundException;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementProject;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.*;
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
  private final ValidationService validationService;

  /**
   * This will create a Jaggaer Rfx (CCS 'Event' equivalent) based on agreement details. Designed to
   * be called directly from the {@link ProcurementProjectService} during the Jaggaer project
   * creation process.
   *
   * @param projectId CCS project id
   * @param principal
   * @return
   */
  public EventStatus createFromProject(Integer projectId, String principal) {

    // Get project from tenders DB to obtain Jaggaer project id
    ProcurementProject project = retryableTendersDBDelegate.findProcurementProjectById(projectId)
        .orElseThrow(() -> new ResourceNotFoundException("Project '" + projectId + "' not found"));

    var createUpdateRfx = createUpdateRfxRequest(project, principal);

    CreateUpdateRfxResponse createRfxResponse =
        ofNullable(jaggaerWebClient.post().uri(jaggaerAPIConfig.getCreateRfx().get(ENDPOINT))
            .bodyValue(createUpdateRfx).retrieve().bodyToMono(CreateUpdateRfxResponse.class)
            .block(ofSeconds(jaggaerAPIConfig.getTimeoutDuration())))
                .orElseThrow(() -> new JaggaerApplicationException(INTERNAL_SERVER_ERROR.value(),
                    "Unexpected error creating Rfx"));

    if (createRfxResponse.getReturnCode() != 0
        || !createRfxResponse.getReturnMessage().equals(Constants.OK)) {
      log.error(createRfxResponse.toString());
      throw new JaggaerApplicationException(createRfxResponse.getReturnCode(),
          createRfxResponse.getReturnMessage());
    }
    log.info("Created event: {}", createRfxResponse);

    // Persist the Jaggaer Rfx details as a new event in the tenders DB
    String ocdsAuthority = ocdsConfig.getAuthority();
    String ocidPrefix = ocdsConfig.getOcidPrefix();
    String eventName = createUpdateRfx.getRfx().getRfxSetting().getShortDescription();

    var procurementEvent = retryableTendersDBDelegate
        .save(ProcurementEvent.of(project, eventName, createRfxResponse.getRfxId(),
            createRfxResponse.getRfxReferenceCode(), ocdsAuthority, ocidPrefix, principal));

    return tendersAPIModelUtils.buildEventStatus(projectId, procurementEvent.getEventID(),
        eventName, createRfxResponse.getRfxReferenceCode(), TEMP_EVENT_TYPE, TenderStatus.PLANNING,
        EVENT_STAGE);
  }

  /**
   * This endpoint will create a Jaggaer Rfx (CCS 'Event' equivalent) on an existing project.
   *
   * @param projectId CCS project id
   * @param eventRequest TODO: Use new detail e.g. downselectedSuppliers
   * @param principal
   * @return
   */
  public EventStatus createFromEventRequest(final Integer projectId,
      final EventRequest eventRequest, final String principal) {
    // Use of Tender is redundant as none of the data is currently required (may change)
    return createFromProject(projectId, principal);
  }

  /**
   * Create Jaggaer request object.
   */
  private CreateUpdateRfx createUpdateRfxRequest(final ProcurementProject project,
      final String principal) {

    // Fetch Jaggaer ID and Buyer company ID from Jaggaer profile based on OIDC login id
    var jaggaerUserId = userProfileService.resolveJaggaerUserId(principal);
    var jaggaerBuyerCompanyId = userProfileService.resolveJaggaerBuyerCompanyId(principal);

    var eventName = getDefaultEventTitle(project.getProjectName(), TEMP_EVENT_TYPE.getValue());
    var buyerCompany = BuyerCompany.builder().id(jaggaerBuyerCompanyId).build();
    var ownerUser = OwnerUser.builder().id(jaggaerUserId).build();

    var rfxSetting =
        RfxSetting.builder().rfiFlag(RFI_FLAG).tenderReferenceCode(project.getExternalReferenceId())
            .templateReferenceCode(jaggaerAPIConfig.getCreateRfx().get("templateId"))
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
   * Update the name, i.e. 'short description' of a Jaggaer Rfx record.
   *
   * @param projectId
   * @param eventId CCS generated event Id, e.g. 'ocds-b5fd17-2'
   * @param eventName the new name
   */
  public void updateProcurementEventName(final Integer projectId, final String eventId,
      final String eventName, final String principal) {

    Assert.hasLength(eventName, "New event name must be supplied");

    // Get event from tenders DB to obtain Jaggaer project id
    var event = validationService.validateProjectAndEventIds(projectId, eventId);

    // Update Jaggaer
    var rfxSetting = RfxSetting.builder().rfxId(event.getExternalEventId())
        .rfxReferenceCode(event.getExternalReferenceId()).shortDescription(eventName).build();

    var rfx = new Rfx(rfxSetting, null, null);

    var createRfxResponse =
        ofNullable(jaggaerWebClient.post().uri(jaggaerAPIConfig.getCreateRfx().get(ENDPOINT))
            .bodyValue(new CreateUpdateRfx(OperationCode.UPDATE, rfx)).retrieve()
            .bodyToMono(CreateUpdateRfxResponse.class)
            .block(ofSeconds(jaggaerAPIConfig.getTimeoutDuration())))
                .orElseThrow(() -> new JaggaerApplicationException(INTERNAL_SERVER_ERROR.value(),
                    "Unexpected error updating Rfx"));

    if (createRfxResponse.getReturnCode() != 0
        || !createRfxResponse.getReturnMessage().equals(Constants.OK)) {
      log.error(createRfxResponse.toString());
      throw new JaggaerApplicationException(createRfxResponse.getReturnCode(),
          createRfxResponse.getReturnMessage());
    }
    log.info("Updated event: {}", createRfxResponse);

    // Save update to local Tenders DB
    event.setEventName(eventName);
    event.setUpdatedAt(Instant.now());
    event.setUpdatedBy(principal);
    retryableTendersDBDelegate.save(event);
  }

  /**
   * Retrieve a single event based on the ID
   *
   * @param projectId
   * @param eventId
   * @param principal
   * @return the converted Tender object
   */
  public EventDetail getEvent(final Integer projectId, final String eventId,
      final String principal) {

    // Get event from tenders DB to obtain Jaggaer project id
    var event = validationService.validateProjectAndEventIds(projectId, eventId);
    var exportRfxUri = jaggaerAPIConfig.getExportRfx().get(ENDPOINT);

    var exportRfxResponse =
        ofNullable(jaggaerWebClient.get().uri(exportRfxUri, event.getExternalEventId()).retrieve()
            .bodyToMono(ExportRfxResponse.class)
            .block(ofSeconds(jaggaerAPIConfig.getTimeoutDuration())))
                .orElseThrow(() -> new JaggaerApplicationException(INTERNAL_SERVER_ERROR.value(),
                    "Unexpected error updating Rfx"));

    return tendersAPIModelUtils.buildEventDetail(exportRfxResponse.getRfxSetting());
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
    return String.format(jaggaerAPIConfig.getCreateRfx().get("defaultTitleFormat"), projectName,
        eventType);
  }

}
