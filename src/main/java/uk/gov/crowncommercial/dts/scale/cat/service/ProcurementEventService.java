package uk.gov.crowncommercial.dts.scale.cat.service;

import static java.time.Duration.ofSeconds;
import static java.util.Objects.requireNonNullElse;
import static java.util.Optional.ofNullable;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig.ENDPOINT;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.io.FilenameUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.config.Constants;
import uk.gov.crowncommercial.dts.scale.cat.config.DocumentConfig;
import uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.config.OcdsConfig;
import uk.gov.crowncommercial.dts.scale.cat.exception.AuthorisationFailureException;
import uk.gov.crowncommercial.dts.scale.cat.exception.JaggaerApplicationException;
import uk.gov.crowncommercial.dts.scale.cat.exception.ResourceNotFoundException;
import uk.gov.crowncommercial.dts.scale.cat.model.DocumentAttachment;
import uk.gov.crowncommercial.dts.scale.cat.model.DocumentKey;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.OrganisationMapping;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementProject;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.*;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.Tender;
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

  private static final Integer RFI_FLAG = 0;
  private static final String RFX_TYPE = "STANDARD_ITT";
  private static final String ADDITIONAL_INFO_FRAMEWORK_NAME = "Framework Name";
  private static final String ADDITIONAL_INFO_LOT_NUMBER = "Lot Number";
  private static final String ADDITIONAL_INFO_LOCALE = "en_GB";
  private static final ReleaseTag EVENT_STAGE = ReleaseTag.TENDER;
  private static final String SUPPLIER_NOT_FOUND_MSG =
      "Organisation id '%s' not found in organisation mappings";

  private final UserProfileService userProfileService;
  private final CriteriaService criteriaService;
  private final OcdsConfig ocdsConfig;
  private final WebClient jaggaerWebClient;
  private final RetryableTendersDBDelegate retryableTendersDBDelegate;
  private final TendersAPIModelUtils tendersAPIModelUtils;
  private final ValidationService validationService;
  private final SupplierService supplierService;
  private final DocumentConfig documentConfig;

  // TODO: switch remaining direct Jaggaer calls to use jaggaerService
  private final JaggaerAPIConfig jaggaerAPIConfig;
  private final JaggaerService jaggaerService;

  /**
   * Creates a Jaggaer Rfx (CCS 'Event' equivalent). Will use {@link Tender#getTitle()} for the
   * event name, if specified, otherwise falls back on the default event title logic (using the
   * project name).
   *
   * Creates with a default event type of 'TBD'.
   *
   * @param projectId CCS project id
   * @param createEvent wraps non-OCDS and OCDS details of the event
   * @param downSelectedSuppliers will default to FALSE if null
   * @param principal
   * @return
   */
  public EventSummary createEvent(final Integer projectId, final CreateEvent createEvent,
      Boolean downselectedSuppliers, final String principal) {

    // Get project from tenders DB to obtain Jaggaer project id
    var project = retryableTendersDBDelegate.findProcurementProjectById(projectId)
        .orElseThrow(() -> new ResourceNotFoundException("Project '" + projectId + "' not found"));

    // Set defaults if no values supplied
    var createEventNonOCDS = requireNonNullElse(createEvent.getNonOCDS(), new CreateEventNonOCDS());
    var createEventOCDS = requireNonNullElse(createEvent.getOCDS(), new CreateEventOCDS());
    var eventTypeValue = ofNullable(createEventNonOCDS.getEventType())
        .map(DefineEventType::getValue).orElseGet(ViewEventType.TBD::getValue);

    downselectedSuppliers = requireNonNullElse(downselectedSuppliers, Boolean.FALSE);

    var eventName = requireNonNullElse(createEventOCDS.getTitle(),
        getDefaultEventTitle(project.getProjectName(), eventTypeValue));

    var createUpdateRfx = createRfxRequest(project, eventName, principal);

    var createRfxResponse =
        ofNullable(jaggaerWebClient.post().uri(jaggaerAPIConfig.getCreateRfx().get(ENDPOINT))
            .bodyValue(createUpdateRfx).retrieve().bodyToMono(CreateUpdateRfxResponse.class)
            .block(ofSeconds(jaggaerAPIConfig.getTimeoutDuration())))
                .orElseThrow(() -> new JaggaerApplicationException(INTERNAL_SERVER_ERROR.value(),
                    "Unexpected error creating Rfx"));

    if (createRfxResponse.getReturnCode() != 0
        || !Constants.JAGGAER_GET_OK_MSG.equals(createRfxResponse.getReturnMessage())) {
      log.error(createRfxResponse.toString());
      throw new JaggaerApplicationException(createRfxResponse.getReturnCode(),
          createRfxResponse.getReturnMessage());
    }
    log.info("Created event: {}", createRfxResponse);

    // Persist the Jaggaer Rfx details as a new event in the tenders DB
    var ocdsAuthority = ocdsConfig.getAuthority();
    var ocidPrefix = ocdsConfig.getOcidPrefix();

    var event = ProcurementEvent.builder().project(project).eventName(eventName)
        .externalEventId(createRfxResponse.getRfxId()).eventType(eventTypeValue)
        .downSelectedSuppliers(downselectedSuppliers)
        .externalReferenceId(createRfxResponse.getRfxReferenceCode())
        .ocdsAuthorityName(ocdsAuthority).ocidPrefix(ocidPrefix).createdBy(principal)
        .createdAt(Instant.now()).updatedBy(principal).updatedAt(Instant.now()).build();

    var procurementEvent = retryableTendersDBDelegate.save(event);

    return tendersAPIModelUtils.buildEventSummary(procurementEvent.getEventID(), eventName,
        createRfxResponse.getRfxReferenceCode(), ViewEventType.fromValue(eventTypeValue),
        TenderStatus.PLANNING, EVENT_STAGE);
  }

  /**
   * Create Jaggaer request object.
   */
  private CreateUpdateRfx createRfxRequest(final ProcurementProject project, final String eventName,
      final String principal) {

    // Fetch Jaggaer ID and Buyer company ID from Jaggaer profile based on OIDC login id
    var jaggaerUserId = userProfileService.resolveBuyerUserByEmail(principal)
        .orElseThrow(() -> new AuthorisationFailureException("Jaggaer user not found")).getUserId();
    var jaggaerBuyerCompanyId =
        userProfileService.resolveBuyerCompanyByEmail(principal).getBravoId();

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

    var suppliersList = SuppliersList.builder()
        .supplier(supplierService.resolveSuppliers(project.getCaNumber(), project.getLotNumber()))
        .build();
    var rfx = Rfx.builder().rfxSetting(rfxSetting).rfxAdditionalInfoList(rfxAdditionalInfoList)
        .suppliersList(suppliersList).build();

    return new CreateUpdateRfx(OperationCode.CREATE_FROM_TEMPLATE, rfx);
  }

  /**
   * Retrieve a single event based on the ID
   *
   * @param projectId
   * @param eventId
   * @return the converted Tender object
   */
  public EventDetail getEvent(final Integer projectId, final String eventId) {
    var event = validationService.validateProjectAndEventIds(projectId, eventId);
    var exportRfxResponse = jaggaerService.getRfx(event.getExternalEventId());

    var buyerQuestions =
        new ArrayList<EvalCriteria>(criteriaService.getEvalCriteria(projectId, eventId, true));

    return tendersAPIModelUtils.buildEventDetail(exportRfxResponse.getRfxSetting(), event,
        buyerQuestions);
  }

  /**
   * Update Event. If no values are set the method does not carry out updates and no errors are
   * thrown.
   *
   * @param procId
   * @param eventId
   * @param updateEvent
   * @param principal
   */
  public EventSummary updateProcurementEvent(final Integer procId, final String eventId,
      final UpdateEvent updateEvent, final String principal) {

    log.debug("Update Event {}", updateEvent);

    var event = validationService.validateProjectAndEventIds(procId, eventId);
    var rfxSetting = RfxSetting.builder().rfxId(event.getExternalEventId())
        .rfxReferenceCode(event.getExternalReferenceId()).build();
    var rfx = Rfx.builder().rfxSetting(rfxSetting).build();
    var updateJaggaer = false;
    var updateDB = false;

    // Update event name
    if (updateEvent.getName() != null && !updateEvent.getName().isEmpty()) {
      rfx.getRfxSetting().setShortDescription(updateEvent.getName());
      event.setEventName(updateEvent.getName());
      updateJaggaer = true;
      updateDB = true;
    }

    // Update event type
    if (updateEvent.getEventType() != null) {
      var existingEventType = event.getEventType();
      if (existingEventType == null || existingEventType.isBlank()
          || event.getEventType().equals("TBD")) {
        event.setEventType(updateEvent.getEventType().getValue());
        updateDB = true;
      } else {
        throw new IllegalArgumentException(
            "Cannot update an existing event type of '" + event.getEventType() + "'");
      }
    }

    // Save to Jaggaer
    if (updateJaggaer) {
      jaggaerService.createUpdateRfx(rfx, OperationCode.CREATEUPDATE);
    }

    // Save to Tenders DB
    if (updateDB) {
      event.setUpdatedAt(Instant.now());
      event.setUpdatedBy(principal);
      retryableTendersDBDelegate.save(event);
    }

    // Build EventSummary response (eventStage is always 'tender')
    var exportRfxResponse = jaggaerService.getRfx(event.getExternalEventId());
    var status = jaggaerAPIConfig.getRfxStatusToTenderStatus()
        .get(exportRfxResponse.getRfxSetting().getStatusCode());
    return tendersAPIModelUtils.buildEventSummary(eventId, event.getEventName(),
        event.getExternalReferenceId(), ViewEventType.fromValue(event.getEventType()),
        TenderStatus.fromValue(status.toString()), EVENT_STAGE);

  }

  /**
   * Get all suppliers on an event.
   *
   * @param procId
   * @param eventId
   * @return
   */
  public Collection<OrganizationReference> getSuppliers(final Integer procId,
      final String eventId) {

    log.debug("Get suppliers for event '{}'", eventId);

    var event = validationService.validateProjectAndEventIds(procId, eventId);
    var existingRfx = jaggaerService.getRfx(event.getExternalEventId());
    var orgs = new ArrayList<OrganizationReference>();

    if (existingRfx.getSuppliersList().getSupplier() != null) {
      existingRfx.getSuppliersList().getSupplier().stream().map(s -> {

        var om = retryableTendersDBDelegate
            .findOrganisationMappingByExternalOrganisationId(s.getCompanyData().getId())
            .orElseThrow(() -> new IllegalArgumentException(
                String.format(SUPPLIER_NOT_FOUND_MSG, s.getCompanyData().getId())));

        var org = new OrganizationReference();
        org.setId(String.valueOf(om.getOrganisationId()));
        org.setName(s.getCompanyData().getName());
        return org;
      }).forEachOrdered(orgs::add);
    }

    return orgs;
  }

  /**
   * Add a supplier to an event.
   *
   * Jaggaer will add any suppliers it does not already have associated to the event, so only those
   * suppliers need to be included.
   *
   * @param procId
   * @param eventId
   * @param organisationReference
   * @return
   */
  public OrganizationReference addSupplier(final Integer procId, final String eventId,
      final OrganizationReference organisationReference) {

    log.debug("Add supplier '{}' to event '{}'", organisationReference, eventId);

    var event = validationService.validateProjectAndEventIds(procId, eventId);

    OrganisationMapping om = retryableTendersDBDelegate
        .findOrganisationMappingByOrganisationId(organisationReference.getId())
        .orElseThrow(() -> new IllegalArgumentException(
            String.format(SUPPLIER_NOT_FOUND_MSG, organisationReference.getId())));

    var companyData = CompanyData.builder().id(om.getExternalOrganisationId()).build();
    var supplier = Supplier.builder().companyData(companyData).build();
    var suppliersList = SuppliersList.builder().supplier(Arrays.asList(supplier)).build();

    // Build Rfx and update
    var rfxSetting = RfxSetting.builder().rfxId(event.getExternalEventId())
        .rfxReferenceCode(event.getExternalReferenceId()).build();
    var rfx = Rfx.builder().rfxSetting(rfxSetting).suppliersList(suppliersList).build();
    jaggaerService.createUpdateRfx(rfx, OperationCode.CREATEUPDATE);

    return organisationReference;

  }

  /**
   * Delete a supplier from an event.
   *
   * Jaggaer does not support a delete operation for suppliers, so the whole list needs to be
   * updated as part of an 'UPDATE_RESET' operation.
   *
   * @param procId
   * @param eventId
   * @param organisationId
   */
  public void deleteSupplier(final Integer procId, final String eventId,
      final String organisationId) {

    log.debug("Delete supplier '{}' from event '{}'", organisationId, eventId);

    var event = validationService.validateProjectAndEventIds(procId, eventId);

    // Determine Jaggaer supplier id
    OrganisationMapping om =
        retryableTendersDBDelegate.findOrganisationMappingByOrganisationId(organisationId)
            .orElseThrow(() -> new IllegalArgumentException(
                String.format(SUPPLIER_NOT_FOUND_MSG, organisationId)));

    // Get all current suppliers on Rfx and remove the one we want to delete
    ExportRfxResponse existingRfx = jaggaerService.getRfx(event.getExternalEventId());
    List<Supplier> updatedSuppliersList = existingRfx.getSuppliersList().getSupplier().stream()
        .filter(s -> !s.getCompanyData().getId().equals(om.getExternalOrganisationId()))
        .collect(Collectors.toList());
    var suppliersList = SuppliersList.builder().supplier(updatedSuppliersList).build();

    // Build Rfx and update
    var rfxSetting = RfxSetting.builder().rfxId(event.getExternalEventId())
        .rfxReferenceCode(event.getExternalReferenceId()).build();
    var rfx = Rfx.builder().rfxSetting(rfxSetting).suppliersList(suppliersList).build();
    jaggaerService.createUpdateRfx(rfx, OperationCode.UPDATE_RESET);

  }

  private String getDefaultEventTitle(final String projectName, final String eventType) {
    return String.format(jaggaerAPIConfig.getCreateRfx().get("defaultTitleFormat"), projectName,
        eventType);
  }

  /**
   * Returns a list of document attachments at the event level.
   *
   * @param procId
   * @param eventId
   * @return
   */
  public Collection<DocumentSummary> getDocumentSummaries(final Integer procId,
      final String eventId) {

    var event = validationService.validateProjectAndEventIds(procId, eventId);
    var exportRfxResponse = jaggaerService.getRfx(event.getExternalEventId());
    Collection<DocumentSummary> documents = new ArrayList<>();

    if (exportRfxResponse.getBuyerAttachmentsList().getAttachment() == null) {
      exportRfxResponse.setBuyerAttachmentsList(
          BuyerAttachmentsList.builder().attachment(new ArrayList<>()).build());
    }
    exportRfxResponse.getBuyerAttachmentsList().getAttachment().stream()
        .map(ba -> tendersAPIModelUtils.buildDocumentSummary(ba, DocumentAudienceType.BUYER))
        .forEachOrdered(documents::add);

    if (exportRfxResponse.getSellerAttachmentsList().getAttachment() == null) {
      exportRfxResponse.setSellerAttachmentsList(
          SellerAttachmentsList.builder().attachment(new ArrayList<>()).build());
    }
    exportRfxResponse.getSellerAttachmentsList().getAttachment().stream()
        .map(ba -> tendersAPIModelUtils.buildDocumentSummary(ba, DocumentAudienceType.SUPPLIER))
        .forEachOrdered(documents::add);

    return documents;
  }

  /**
   * Uploads a document to a Jaggaer Rfx.
   *
   * @param procId
   * @param eventId
   * @param multipartFile
   * @param audience
   * @param fileDescription
   * @return
   */
  public DocumentSummary uploadDocument(final Integer procId, final String eventId,
      final MultipartFile multipartFile, final DocumentAudienceType audience,
      final String fileDescription) {

    log.debug("Upload Document to event {}", eventId);

    final String fileName = multipartFile.getOriginalFilename();
    final String extension = FilenameUtils.getExtension(fileName);

    // Validate file extension
    if (!documentConfig.getAllowedExtentions().contains(extension.toLowerCase())) {
      throw new IllegalArgumentException("File is not one of the allowed types: "
          + documentConfig.getAllowedExtentions().toString());
    }

    // Validate file size
    if (multipartFile.getSize() > documentConfig.getMaxSize()) {
      throw new IllegalArgumentException("File is too large: " + multipartFile.getSize()
          + " bytes. Maximum allowed upload size is: " + documentConfig.getMaxSize() + " bytes");
    }

    // Validate total file size
    Collection<DocumentSummary> currentDocuments = getDocumentSummaries(procId, eventId);
    Long totalEventFileSize =
        currentDocuments.stream().map(DocumentSummary::getFileSize).reduce(0L, Long::sum);
    if (Long.sum(totalEventFileSize, multipartFile.getSize()) > documentConfig.getMaxTotalSize()) {
      throw new IllegalArgumentException(
          "Uploading file will exceed the maximum allowed total limit of "
              + documentConfig.getMaxTotalSize() + " bytes for event " + eventId
              + " (current total size is " + totalEventFileSize + " bytes, across "
              + currentDocuments.size() + " files)");
    }

    var event = validationService.validateProjectAndEventIds(procId, eventId);
    var rfxSetting = RfxSetting.builder().rfxId(event.getExternalEventId())
        .rfxReferenceCode(event.getExternalReferenceId()).build();
    var attachment =
        Attachment.builder().fileName(fileName).fileDescription(fileDescription).build();
    Rfx rfx;

    switch (audience) {
      case BUYER:
        var bal = BuyerAttachmentsList.builder().attachment(Arrays.asList(attachment)).build();
        rfx = Rfx.builder().rfxSetting(rfxSetting).buyerAttachmentsList(bal).build();
        break;
      case SUPPLIER:
        var sal = SellerAttachmentsList.builder().attachment(Arrays.asList(attachment)).build();
        rfx = Rfx.builder().rfxSetting(rfxSetting).sellerAttachmentsList(sal).build();
        break;
      default:
        throw new IllegalArgumentException("Unsupported audience for document upload");
    }

    var update = new CreateUpdateRfx(OperationCode.CREATEUPDATE, rfx);
    jaggaerService.uploadDocument(multipartFile, update);

    Collection<DocumentSummary> docs = getDocumentSummaries(procId, eventId);
    return docs.stream().filter(d -> d.getFileName().equals(fileName)).findFirst().orElseThrow(
        () -> new IllegalStateException("There was an unexpected error uploading the document"));
  }

  /**
   * Retrieve a document attached to an Rfx in Jaggaer.
   *
   * @param procId
   * @param eventId
   * @param documentId
   * @return
   */
  public DocumentAttachment getDocument(final Integer procId, final String eventId,
      final String documentId) {

    log.debug("Get Document {} from Event {}", documentId, eventId);

    validationService.validateProjectAndEventIds(procId, eventId);
    var documentKey = DocumentKey.fromString(documentId);
    log.debug("Retrieving Document {}", documentKey.getFileName());
    return jaggaerService.getDocument(documentKey.getFileId(), documentKey.getFileName());
  }

}
