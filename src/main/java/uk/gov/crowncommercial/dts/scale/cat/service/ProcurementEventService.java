package uk.gov.crowncommercial.dts.scale.cat.service;

import static java.util.Objects.requireNonNullElse;
import static java.util.Optional.ofNullable;
import static uk.gov.crowncommercial.dts.scale.cat.config.Constants.ASSESSMENT_EVENT_TYPES;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import javax.transaction.Transactional;
import org.apache.commons.io.FilenameUtils;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
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
import uk.gov.crowncommercial.dts.scale.cat.model.entity.*;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.*;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.Tender;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.*;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;
import uk.gov.crowncommercial.dts.scale.cat.service.ca.AssessmentService;
import uk.gov.crowncommercial.dts.scale.cat.service.documentupload.DocumentUploadService;
import uk.gov.crowncommercial.dts.scale.cat.utils.ByteArrayMultipartFile;
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
  private static final String ERR_MSG_FMT_SUPPLIER_NOT_FOUND =
      "Organisation id '%s' not found in organisation mappings";
  public static final String ERR_MSG_JAGGAER_USER_NOT_FOUND = "Jaggaer user not found";
  public static final String ERR_MSG_FMT_DOCUMENT_NOT_FOUND =
      "Document upload record for ID [%s] not found";

  private final UserProfileService userProfileService;
  private final CriteriaService criteriaService;
  private final OcdsConfig ocdsConfig;
  private final WebClient jaggaerWebClient;
  private final RetryableTendersDBDelegate retryableTendersDBDelegate;
  private final TendersAPIModelUtils tendersAPIModelUtils;
  private final ValidationService validationService;
  private final SupplierService supplierService;
  private final DocumentConfig documentConfig;
  private final AssessmentService assessmentService;
  private final DocumentUploadService documentUploadService;

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
      Boolean downSelectedSuppliers, final String principal) {

    // Get project from tenders DB to obtain Jaggaer project id
    var project = retryableTendersDBDelegate.findProcurementProjectById(projectId)
        .orElseThrow(() -> new ResourceNotFoundException("Project '" + projectId + "' not found"));

    // Set defaults if no values supplied
    var createEventNonOCDS = requireNonNullElse(createEvent.getNonOCDS(), new CreateEventNonOCDS());
    var createEventOCDS = requireNonNullElse(createEvent.getOCDS(), new CreateEventOCDS());
    var eventTypeValue = ofNullable(createEventNonOCDS.getEventType())
        .map(DefineEventType::getValue).orElseGet(ViewEventType.TBD::getValue);

    downSelectedSuppliers = requireNonNullElse(downSelectedSuppliers, Boolean.FALSE);

    var eventName = StringUtils.hasText(createEventOCDS.getTitle()) ? createEventOCDS.getTitle()
        : getDefaultEventTitle(project.getProjectName(), eventTypeValue);

    var eventBuilder = ProcurementEvent.builder();

    // Optional return values
    Integer returnAssessmentId = null;
    String rfxReferenceCode = null;

    if (createEventNonOCDS.getEventType() != null
        && ASSESSMENT_EVENT_TYPES.contains(createEventNonOCDS.getEventType())) {

      // Either create a new assessment or validate and link to existing one
      if (createEvent.getNonOCDS().getAssessmentId() == null) {
        var newAssessmentId = assessmentService.createEmptyAssessment(project.getCaNumber(),
            project.getLotNumber(), createEventNonOCDS.getEventType(), principal);
        eventBuilder.assessmentId(newAssessmentId);
        returnAssessmentId = newAssessmentId;
        log.debug("Created new empty assessment: {}", newAssessmentId);
      } else {
        var validatedAssessment =
            assessmentService.getAssessment(createEvent.getNonOCDS().getAssessmentId(), principal);
        eventBuilder.assessmentId(validatedAssessment.getAssessmentId());
        returnAssessmentId = validatedAssessment.getAssessmentId();
        log.debug("Linking existing assessment: {} to new event",
            validatedAssessment.getAssessmentId());
      }
    } else {

      var createUpdateRfx = createRfxRequest(project, eventName, principal);

      var createRfxResponse = jaggaerService.createUpdateRfx(createUpdateRfx.getRfx(),
          createUpdateRfx.getOperationCode());

      if (createRfxResponse.getReturnCode() != 0
          || !Constants.OK_MSG.equals(createRfxResponse.getReturnMessage())) {
        log.error(createRfxResponse.toString());
        throw new JaggaerApplicationException(createRfxResponse.getReturnCode(),
            createRfxResponse.getReturnMessage());
      }
      log.info("Created Jaggaer (Rfx) event: {}", createRfxResponse);
      rfxReferenceCode = createRfxResponse.getRfxReferenceCode();
      eventBuilder.externalEventId(createRfxResponse.getRfxId())
          .externalReferenceId(createRfxResponse.getRfxReferenceCode());
    }

    // Persist the Jaggaer Rfx details as a new event in the tenders DB
    var ocdsAuthority = ocdsConfig.getAuthority();
    var ocidPrefix = ocdsConfig.getOcidPrefix();

    var exportRfxResponse = jaggaerService.getRfx(eventBuilder.build().getExternalEventId());
    var tenderStatus = TenderStatus.PLANNING.getValue();

    if (exportRfxResponse.getRfxSetting() != null) {
      var rfxStatus = jaggaerAPIConfig.getRfxStatusAndEventTypeToTenderStatus()
          .get(exportRfxResponse.getRfxSetting().getStatusCode());

      tenderStatus = rfxStatus != null && rfxStatus.get(eventTypeValue) != null ?
          rfxStatus.get(eventTypeValue).getValue() :
          null;
      if (exportRfxResponse.getRfxSetting().getPublishDate() != null) {
        eventBuilder.publishDate(exportRfxResponse.getRfxSetting().getPublishDate().toInstant());
      }
      if (exportRfxResponse.getRfxSetting().getCloseDate() != null) {
        eventBuilder.closeDate(exportRfxResponse.getRfxSetting().getCloseDate().toInstant());
      }
    }

    eventBuilder.project(project).eventName(eventName).eventType(eventTypeValue)
        .downSelectedSuppliers(downSelectedSuppliers).ocdsAuthorityName(ocdsAuthority)
        .ocidPrefix(ocidPrefix).createdBy(principal).createdAt(Instant.now()).updatedBy(principal)
        .updatedAt(Instant.now()).tenderStatus(tenderStatus);

    var event = eventBuilder.build();

    ProcurementEvent procurementEvent;

    // If event is an AssessmentType - add suppliers to Tenders DB (as no event exists in Jaggaer)
    if (createEventNonOCDS.getEventType() != null
        && ASSESSMENT_EVENT_TYPES.contains(createEventNonOCDS.getEventType())) {
      procurementEvent = addSuppliersToTendersDB(event,
          supplierService.getSuppliersForLot(project.getCaNumber(), project.getLotNumber()), true,
          principal);
    } else {
      procurementEvent = retryableTendersDBDelegate.save(event);
    }

    return tendersAPIModelUtils.buildEventSummary(procurementEvent.getEventID(), eventName,
        Optional.ofNullable(rfxReferenceCode), ViewEventType.fromValue(eventTypeValue),
        TenderStatus.PLANNING, EVENT_STAGE, Optional.ofNullable(returnAssessmentId));
  }

  /**
   * Create Jaggaer request object.
   */
  private CreateUpdateRfx createRfxRequest(final ProcurementProject project, final String eventName,
      final String principal) {

    // Fetch Jaggaer ID and Buyer company ID from Jaggaer profile based on OIDC login id
    var jaggaerUserId = userProfileService.resolveBuyerUserByEmail(principal)
        .orElseThrow(() -> new AuthorisationFailureException(ERR_MSG_JAGGAER_USER_NOT_FOUND))
        .getUserId();
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

    var getDataTemplate =
        !event.isAssessment() && !ViewEventType.TBD.name().equals(event.getEventType());

    return tendersAPIModelUtils.buildEventDetail(exportRfxResponse.getRfxSetting(), event,
        getDataTemplate ? criteriaService.getEvalCriteria(projectId, eventId, true)
            : Collections.emptySet());
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
    validationService.validateUpdateEventAssessment(updateEvent, event, principal);

    var rfxSetting = RfxSetting.builder().rfxId(event.getExternalEventId())
        .rfxReferenceCode(event.getExternalReferenceId()).build();
    var rfx = Rfx.builder().rfxSetting(rfxSetting).build();
    var updateJaggaer = false;
    var updateDB = false;
    var createAssessment = false;
    Integer returnAssessmentId = null;

    // Update event name
    if (StringUtils.hasText(updateEvent.getName())) {
      rfx.getRfxSetting().setShortDescription(updateEvent.getName());
      event.setEventName(updateEvent.getName());

      // TODO: Should pre-existing DAA/FCA events have corresponding Jaggaer Rfx? (confirm
      // via SCAT-2501)
      updateJaggaer = true;
      updateDB = true;
    }

    // Update event type
    if (updateEvent.getEventType() != null) {
      if (!ViewEventType.TBD.name().equals(event.getEventType())) {
        throw new IllegalArgumentException(
            "Cannot update an existing event type of '" + event.getEventType() + "'");
      }

      if (ASSESSMENT_EVENT_TYPES.contains(updateEvent.getEventType())
          && updateEvent.getAssessmentId() == null && event.getAssessmentId() == null) {
        createAssessment = true;
      }

      event.setEventType(updateEvent.getEventType().getValue());
      updateDB = true;
    }

    // Valid to supply either for an existing event
    if (updateEvent.getAssessmentId() != null
        || updateEvent.getAssessmentSupplierTarget() != null) {
      event.setAssessmentSupplierTarget(updateEvent.getAssessmentSupplierTarget());
      event.setAssessmentId(updateEvent.getAssessmentId());
      updateDB = true;
    }

    // Create a new empty assessment
    if (createAssessment) {
      returnAssessmentId = assessmentService.createEmptyAssessment(event.getProject().getCaNumber(),
          event.getProject().getLotNumber(), updateEvent.getEventType(), principal);
    } else if (updateEvent.getAssessmentId() != null) {
      // Return the existing (validated) assessmentId
      returnAssessmentId = updateEvent.getAssessmentId();
    }

    // Save to Jaggaer
    if (updateJaggaer) {
      jaggaerService.createUpdateRfx(rfx, OperationCode.CREATEUPDATE);
    }

    var exportRfxResponse = jaggaerService.getRfx(event.getExternalEventId());

    // Save to Tenders DB
    if (updateDB) {

      var tenderStatus = TenderStatus.PLANNING.getValue();
      if (exportRfxResponse.getRfxSetting() != null) {
        var rfxStatus = jaggaerAPIConfig.getRfxStatusAndEventTypeToTenderStatus()
            .get(exportRfxResponse.getRfxSetting().getStatusCode());

        tenderStatus = rfxStatus != null && rfxStatus.get(event.getEventType()) != null ?
            rfxStatus.get(event.getEventType()).getValue() :
            null;
      }

      event.setUpdatedAt(Instant.now());
      event.setUpdatedBy(principal);
      event.setAssessmentId(returnAssessmentId);
      if (exportRfxResponse.getRfxSetting().getPublishDate() != null) {
        event.setPublishDate(exportRfxResponse.getRfxSetting().getPublishDate().toInstant());
      }
      if (exportRfxResponse.getRfxSetting().getCloseDate() != null) {
        event.setCloseDate(exportRfxResponse.getRfxSetting().getCloseDate().toInstant());
      }

      if (tenderStatus != null)
        event.setTenderStatus(tenderStatus);
      retryableTendersDBDelegate.save(event);
    }

    // Build EventSummary response (eventStage is always 'tender')
    var status = jaggaerAPIConfig.getRfxStatusToTenderStatus()
        .get(exportRfxResponse.getRfxSetting().getStatusCode());

    return tendersAPIModelUtils.buildEventSummary(eventId, event.getEventName(),
        Optional.ofNullable(event.getExternalReferenceId()),
        ViewEventType.fromValue(event.getEventType()), TenderStatus.fromValue(status.toString()),
        EVENT_STAGE, Optional.ofNullable(returnAssessmentId));
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

    if (event.isTendersDBOnly()) {
      log.debug("Event {} is retrieved from Tenders DB only {}", event.getId(),
          event.getEventType());
      return getSuppliersFromTendersDB(event);
    }
    log.debug("Event {} is retrieved from Jaggaer {}", event.getId(), event.getEventType());
    return getSuppliersFromJaggaer(event);
  }

  /**
   * Add/Overwrite suppliers on an Event.
   *
   * If it is an Assessment Event Type, suppliers will only be added in the Tenders DB, otherwise
   * updates will only be in Jaggaer.
   *
   * @param procId
   * @param eventId
   * @param organisationReferences
   * @param overwrite if <code>true</code> will replace the list of suppliers, otherwise it will
   *        just add to the list.
   * @return
   */
  public Collection<OrganizationReference> addSuppliers(final Integer procId, final String eventId,
      final Collection<OrganizationReference> organisationReferences, final boolean overwrite,
      final String principal) {

    var event = validationService.validateProjectAndEventIds(procId, eventId);

    var supplierOrgIds = organisationReferences.stream().map(OrganizationReference::getId)
        .collect(Collectors.toSet());

    var supplierOrgMappings =
        retryableTendersDBDelegate.findOrganisationMappingByOrganisationIdIn(supplierOrgIds);

    // Validate suppliers exist in Organisation Mapping Table
    if (supplierOrgMappings.size() != organisationReferences.size()) {

      var missingSuppliers = new ArrayList<String>();
      organisationReferences.stream().forEach(or -> {
        if (supplierOrgMappings.parallelStream()
            .filter(som -> som.getOrganisationId().equals(or.getId())).findFirst().isEmpty()) {
          missingSuppliers.add(or.getId());
        }
      });

      if (!missingSuppliers.isEmpty()) {
        throw new ResourceNotFoundException(String.format(
            "The following suppliers are not present in the Organisation Mappings, so unable to add them: %s",
            missingSuppliers));
      }
    }

    /*
     * If Event is a Tenders DB only type, suppliers are stored in the Tenders DB only, otherwise
     * they are stored in Jaggaer.
     */
    if (event.isTendersDBOnly()) {
      log.debug("Event {} is persisted in Tenders DB only {}", event.getEventID(),
          event.getEventType());
      addSuppliersToTendersDB(event, supplierOrgMappings, overwrite, principal);
    } else {
      log.debug("Event {} is persisted in Jaggaer {}", event.getId(), event.getEventType());
      addSuppliersToJaggaer(event, supplierOrgMappings, overwrite);
    }

    return organisationReferences;
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
      final String organisationId, final String principal) {

    log.debug("Delete supplier '{}' from event '{}'", organisationId, eventId);

    var event = validationService.validateProjectAndEventIds(procId, eventId);

    // Determine Jaggaer supplier id
    var om = retryableTendersDBDelegate.findOrganisationMappingByOrganisationId(organisationId)
        .orElseThrow(() -> new IllegalArgumentException(
            String.format(ERR_MSG_FMT_SUPPLIER_NOT_FOUND, organisationId)));

    /*
     * If Event is a Tenders DB only type, suppliers are stored in the Tenders DB only, otherwise
     * they are stored in Jaggaer.
     */
    if (event.isTendersDBOnly()) {
      log.debug("Event {} is persisted in Tenders DB only {}", event.getId(), event.getEventType());
      deleteSupplierFromTendersDB(event, om, principal);
    } else {
      log.debug("Event {} is persisted in Jaggaer {}", event.getId(), event.getEventType());
      deleteSupplierFromJaggaer(event, om);
    }
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
  @Transactional
  public Collection<DocumentSummary> getDocumentSummaries(final Integer procId,
      final String eventId) {

    var event = validationService.validateProjectAndEventIds(procId, eventId);

    return event.getDocumentUploads().stream().map(tendersAPIModelUtils::buildDocumentSummary)
        .collect(Collectors.toList());
  }

  /**
   * Uploads a document to a Jaggaer Rfx.
   *
   * @param procId
   * @param eventId
   * @param multipartFile
   * @param audience
   * @param fileDescription
   * @param principal
   * @return
   */
  @Transactional
  public DocumentSummary uploadDocument(final Integer procId, final String eventId,
      final MultipartFile multipartFile, final DocumentAudienceType audience,
      final String fileDescription, final String principal) {

    log.debug("Upload Document to event {}", eventId);

    final var fileName = multipartFile.getOriginalFilename();
    final var extension = FilenameUtils.getExtension(fileName);

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

    var event = validationService.validateProjectAndEventIds(procId, eventId);

    // Validate total file size
    var totalEventFileSize =
        event.getDocumentUploads().stream().map(DocumentUpload::getSize).reduce(0L, Long::sum);
    if (Long.sum(totalEventFileSize, multipartFile.getSize()) > documentConfig.getMaxTotalSize()) {
      throw new IllegalArgumentException(
          "Uploading file will exceed the maximum allowed total limit of "
              + documentConfig.getMaxTotalSize() + " bytes for event " + eventId
              + " (current total size is " + totalEventFileSize + " bytes, across "
              + event.getDocumentUploads().size() + " files)");
    }

    return tendersAPIModelUtils.buildDocumentSummary(documentUploadService.uploadDocument(event,
        multipartFile, audience, fileDescription, principal));
  }

  /**
   * Retrieve a document from the Tenders document upload store (DB+S3)
   *
   * @param procId
   * @param eventId
   * @param documentId
   * @return
   */
  @Transactional
  public DocumentAttachment getDocument(final Integer procId, final String eventId,
      final String documentId, final String principal) {

    log.debug("Get Document {} from Event {}", documentId, eventId);

    var event = validationService.validateProjectAndEventIds(procId, eventId);
    var documentUpload = findDocumentUploadInEvent(event, documentId);
    var documentKey = DocumentKey.fromString(documentId);
    log.debug("Retrieving Document {}", documentKey.getFileName());

    return DocumentAttachment.builder()
        .data(documentUploadService.retrieveDocument(documentUpload, principal))
        .fileName(documentKey.getFileName())
        .contentType(MediaType.parseMediaType(documentUpload.getMimetype())).build();
  }

  /**
   * Delete a document from the Tenders document upload store (DB+S3)
   *
   * @param procId
   * @param eventId
   * @param documentId
   */
  @Transactional
  public void deleteDocument(final Integer procId, final String eventId, final String documentId) {
    log.debug("Delete Document {} from Event {}", documentId, eventId);

    var event = validationService.validateProjectAndEventIds(procId, eventId);
    var documentUpload = findDocumentUploadInEvent(event, documentId);
    var documentKey = DocumentKey.fromString(documentId);
    log.debug("Deleting Document {}", documentKey.getFileName());

    documentUploadService.deleteDocument(documentUpload);
  }

  /**
   * Publish an Rfx in Jaggaer
   *
   * @param procId
   * @param eventId
   */
  @Transactional
  public void publishEvent(final Integer procId, final String eventId,
      final PublishDates publishDates, final String principal) {

    var jaggaerUserId = userProfileService.resolveBuyerUserByEmail(principal)
        .orElseThrow(() -> new AuthorisationFailureException(ERR_MSG_JAGGAER_USER_NOT_FOUND))
        .getUserId();

    var procurementEvent = validationService.validateProjectAndEventIds(procId, eventId);
    var exportRfxResponse = jaggaerService.getRfx(procurementEvent.getExternalEventId());
    var status = jaggaerAPIConfig.getRfxStatusToTenderStatus()
        .get(exportRfxResponse.getRfxSetting().getStatusCode());

    if (TenderStatus.PLANNED != status) {
      throw new IllegalArgumentException(
          "You cannot publish an event unless it is in a 'planned' state");
    }

    // Fetch and upload all SAFE documents
    procurementEvent.getDocumentUploads().stream()
        .filter(du -> VirusCheckStatus.SAFE == du.getExternalStatus()).forEach(documentUpload -> {
          var docKey = DocumentKey.fromString(documentUpload.getDocumentId());
          var multipartFile = new ByteArrayMultipartFile(
              documentUploadService.retrieveDocument(documentUpload, principal),
              docKey.getFileName(), documentUpload.getMimetype());

          eventUploadDocument(procurementEvent, docKey.getFileName(),
              documentUpload.getDocumentDescription(), documentUpload.getAudience(), multipartFile);
        });

    validationService.validatePublishDates(publishDates);
    jaggaerService.publishRfx(procurementEvent, publishDates, jaggaerUserId);
  }

  /**
   * Upload a document to the Jaggaer event
   *
   * @param event
   * @param fileName
   * @param fileDescription
   * @param audience
   * @param multipartFile
   */
  public void eventUploadDocument(final ProcurementEvent event, final String fileName,
      final String fileDescription, final DocumentAudienceType audience,
      final MultipartFile multipartFile) {

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
  }

  /**
   * Get Summaries of all Events on a Project.
   *
   * @param projectId
   * @return
   */
  @Transactional
  public List<EventSummary> getEventsForProject(final Integer projectId, final String principal) {

    var events = retryableTendersDBDelegate.findProcurementEventsByProjectId(projectId);

    return events.stream().map(event -> {

      TenderStatus statusCode;

      if (event.getExternalEventId() == null) {
        var assessment = assessmentService.getAssessment(event.getAssessmentId(), principal);
        statusCode = TenderStatus.fromValue(assessment.getStatus().toString().toLowerCase());
      } else {
        var exportRfxResponse = jaggaerService.getRfx(event.getExternalEventId());
        statusCode = jaggaerAPIConfig.getRfxStatusToTenderStatus()
            .get(exportRfxResponse.getRfxSetting().getStatusCode());
      }

      return tendersAPIModelUtils.buildEventSummary(event.getEventID(), event.getEventName(),
          Optional.ofNullable(event.getExternalEventId()),
          ViewEventType.fromValue(event.getEventType()), statusCode, EVENT_STAGE, Optional.empty());
    }).collect(Collectors.toList());
  }

  /**
   * Add/overwrite suppliers in Tenders DB.
   *
   * @param event
   * @param supplierOrgMappings
   * @param overwrite
   * @return
   */
  private ProcurementEvent addSuppliersToTendersDB(final ProcurementEvent event,
      final Set<OrganisationMapping> supplierOrgMappings, final boolean overwrite,
      final String principal) {

    if (overwrite && event.getCapabilityAssessmentSuppliers() != null) {
      event.getCapabilityAssessmentSuppliers().clear();
    }

    var suppliers = event.getCapabilityAssessmentSuppliers();
    supplierOrgMappings.stream().forEach(org -> {
      if (suppliers != null && suppliers.stream().noneMatch(s -> Objects
          .equals(s.getOrganisationMapping().getOrganisationId(), org.getOrganisationId()))) {
        log.debug("Creating new SupplierSelection record for organisation [{}]",
            org.getOrganisationId());
        var selection = SupplierSelection.builder().organisationMapping(org).eventId(event.getId())
            .createdAt(Instant.now()).createdBy(principal).build();
        event.getCapabilityAssessmentSuppliers().add(selection);
      }
    });
    return retryableTendersDBDelegate.save(event);
  }

  /**
   * Add/overwrite suppliers in Jaggaer.
   *
   * @param event
   * @param supplierOrgMappings
   * @param overwrite
   */
  private void addSuppliersToJaggaer(final ProcurementEvent event,
      final Set<OrganisationMapping> supplierOrgMappings, final boolean overwrite) {

    OperationCode operationCode;
    if (overwrite) {
      operationCode = OperationCode.UPDATE_RESET;
    } else {
      operationCode = OperationCode.CREATEUPDATE;
    }

    var suppliersList = supplierOrgMappings.stream().map(org -> {
      var companyData = CompanyData.builder().id(org.getExternalOrganisationId()).build();
      return Supplier.builder().companyData(companyData).build();
    }).collect(Collectors.toList());

    // Build Rfx and update
    var rfxSetting = RfxSetting.builder().rfxId(event.getExternalEventId())
        .rfxReferenceCode(event.getExternalReferenceId()).build();
    var rfx = Rfx.builder().rfxSetting(rfxSetting)
        .suppliersList(SuppliersList.builder().supplier(suppliersList).build()).build();
    jaggaerService.createUpdateRfx(rfx, operationCode);
  }

  /**
   * Get suppliers on an event from Tenders DB.
   *
   * @param event
   * @return
   */
  private List<OrganizationReference> getSuppliersFromTendersDB(final ProcurementEvent event) {

    return event.getCapabilityAssessmentSuppliers().stream().map(s -> {

      // TODO - Conclave does not work with DUNS numbers (which we have in organisation_mapping
      // table)
      // Nick raised this with Nanu who was going to speak to Brickendon as they should support
      // lookup by DUNS number. This can be reinstated when that is fixed in Conclave.
      // OrganisationProfileResponseInfo org = conclaveService
      // .getOrganisation(s.getOrganisationMapping().getOrganisationId()).orElseThrow(
      // () -> new ResourceNotFoundException(String.format(SUPPLIER_NOT_FOUND_CONCLAVE_MSG,
      // s.getOrganisationMapping().getOrganisationId())));

      var orgRef = new OrganizationReference();
      orgRef.setId(String.valueOf(s.getOrganisationMapping().getOrganisationId()));
      // orgRef.setName(org.getIdentifier().getLegalName()); // see comment above
      return orgRef;
    }).collect(Collectors.toList());
  }

  /**
   * Get suppliers on an event from Jaggaer.
   *
   * @param event
   * @return
   */
  private List<OrganizationReference> getSuppliersFromJaggaer(final ProcurementEvent event) {

    var existingRfx = jaggaerService.getRfx(event.getExternalEventId());
    var orgs = new ArrayList<OrganizationReference>();

    if (existingRfx.getSuppliersList().getSupplier() != null) {
      existingRfx.getSuppliersList().getSupplier().stream().map(s -> {

        var om = retryableTendersDBDelegate
            .findOrganisationMappingByExternalOrganisationId(s.getCompanyData().getId())
            .orElseThrow(() -> new IllegalArgumentException(
                String.format(ERR_MSG_FMT_SUPPLIER_NOT_FOUND, s.getCompanyData().getId())));

        var org = new OrganizationReference();
        org.setId(String.valueOf(om.getOrganisationId()));
        org.setName(s.getCompanyData().getName());
        return org;
      }).forEachOrdered(orgs::add);
    }

    return orgs;
  }

  /**
   * Delete supplier from Tenders DB.
   *
   * @param event
   * @param supplierOrgMappings
   * @param overwrite
   */
  private void deleteSupplierFromTendersDB(final ProcurementEvent event,
      final OrganisationMapping supplierOrgMapping, final String principal) {

    event.setUpdatedAt(Instant.now());
    event.setUpdatedBy(principal);
    retryableTendersDBDelegate.save(event);

    var supplierSelection = event.getCapabilityAssessmentSuppliers().stream()
        .filter(s -> s.getOrganisationMapping().getId().equals(supplierOrgMapping.getId()))
        .findFirst().orElseThrow();

    retryableTendersDBDelegate.delete(supplierSelection);
  }

  /**
   * Delete supplier from Jaggaer.
   *
   * @param event
   */
  private void deleteSupplierFromJaggaer(final ProcurementEvent event,
      final OrganisationMapping supplierOrgMapping) {

    // Get all current suppliers on Rfx and remove the one we want to delete
    var existingRfx = jaggaerService.getRfx(event.getExternalEventId());
    List<Supplier> updatedSuppliersList = existingRfx.getSuppliersList().getSupplier().stream()
        .filter(
            s -> !s.getCompanyData().getId().equals(supplierOrgMapping.getExternalOrganisationId()))
        .collect(Collectors.toList());
    var suppliersList = SuppliersList.builder().supplier(updatedSuppliersList).build();

    // Build Rfx and update
    var rfxSetting = RfxSetting.builder().rfxId(event.getExternalEventId())
        .rfxReferenceCode(event.getExternalReferenceId()).build();
    var rfx = Rfx.builder().rfxSetting(rfxSetting).suppliersList(suppliersList).build();
    jaggaerService.createUpdateRfx(rfx, OperationCode.UPDATE_RESET);
  }

  DocumentUpload findDocumentUploadInEvent(final ProcurementEvent event, final String documentId) {
    return event.getDocumentUploads().stream()
        .filter(du -> Objects.equals(du.getDocumentId(), documentId)).findFirst()
        .orElseThrow(() -> new ResourceNotFoundException(
            String.format(ERR_MSG_FMT_DOCUMENT_NOT_FOUND, documentId)));
  }

}
