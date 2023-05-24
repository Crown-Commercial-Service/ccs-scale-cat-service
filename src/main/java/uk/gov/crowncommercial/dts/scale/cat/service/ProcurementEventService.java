package uk.gov.crowncommercial.dts.scale.cat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

//import org.w3c.dom.events.Event;
import uk.gov.crowncommercial.dts.scale.cat.config.*;

import uk.gov.crowncommercial.dts.scale.cat.exception.AuthorisationFailureException;
import uk.gov.crowncommercial.dts.scale.cat.exception.JaggaerApplicationException;
import uk.gov.crowncommercial.dts.scale.cat.exception.ResourceNotFoundException;
import uk.gov.crowncommercial.dts.scale.cat.exception.TendersDBDataException;
import uk.gov.crowncommercial.dts.scale.cat.model.*;
import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.Assessment;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.*;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.AssessmentStatusEntity;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.AssessmentTool;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.GCloudAssessmentEntity;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.Tender;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.*;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.*;
import uk.gov.crowncommercial.dts.scale.cat.processors.SupplierStore;
import uk.gov.crowncommercial.dts.scale.cat.processors.SupplierStoreFactory;
import uk.gov.crowncommercial.dts.scale.cat.processors.TwoStageEventService;
import uk.gov.crowncommercial.dts.scale.cat.processors.async.AsyncExecutor;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;
import uk.gov.crowncommercial.dts.scale.cat.service.asyncprocessors.JaggaerSupplierEventData;
import uk.gov.crowncommercial.dts.scale.cat.service.asyncprocessors.JaggaerSupplierPush;
import uk.gov.crowncommercial.dts.scale.cat.service.ca.AssessmentService;
import uk.gov.crowncommercial.dts.scale.cat.service.documentupload.DocumentUploadService;
import uk.gov.crowncommercial.dts.scale.cat.service.documentupload.callables.DocumentUploadCallable;
import uk.gov.crowncommercial.dts.scale.cat.service.documentupload.callables.RetrieveDocumentCallable;
import uk.gov.crowncommercial.dts.scale.cat.utils.ByteArrayMultipartFile;
import uk.gov.crowncommercial.dts.scale.cat.utils.TendersAPIModelUtils;

import javax.transaction.Transactional;
import javax.validation.ValidationException;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.String.format;
import static java.util.Objects.requireNonNullElse;
import static java.util.Optional.ofNullable;
import static uk.gov.crowncommercial.dts.scale.cat.config.Constants.*;
import static uk.gov.crowncommercial.dts.scale.cat.utils.TendersAPIModelUtils.*;

/**
 *
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProcurementEventService implements EventService {

    private static final Integer RFI_FLAG = 0;
    private static final String RFX_TYPE = "STANDARD_ITT";
    private static final String ADDITIONAL_INFO_FRAMEWORK_NAME = "Framework Name";
    private static final String ADDITIONAL_INFO_LOT_NUMBER = "Lot Number";
    private static final String ADDITIONAL_INFO_LOCALE = "en_GB";
    private static final ReleaseTag EVENT_STAGE = ReleaseTag.TENDER;
    private static final String ERR_MSG_FMT_SUPPLIER_NOT_FOUND =
            "Organisation id '%s' not found in organisation mappings";

    public static final String ERR_MSG_FMT_DOCUMENT_NOT_FOUND =
            "Document upload record for ID [%s] not found";
    public static final String ERR_MSG_ALL_DIMENSION_WEIGHTINGS =
            "All dimensions must have 100% weightings prior to the supplier(s) can be added to the event";
    public static final Set<String> RESPONSE_STATES = Set.of("Not Replied");
    private static final String ERR_MSG_FMT_NO_SUPPLIER_RESPONSES_FOUND =
            "No Supplier Responses found for the given event '%s'  ";
    private static final String ERR_MSG_SUPPLIER_NOT_FOUND_CONCLAVE =
            "Supplier [%s] not found in Conclave";
    private static final String ERR_MSG_RFX_NOT_FOUND = "Rfx [%s] not found in Jaggaer";

    private static final String JAGGAER_USER_NOT_FOUND = "Jaggaer user not found";
    private static final String COMPLETE_STATUS = "complete";
    public static final String CONTRACT_DETAILS_NOT_FOUND = "Contract details not found";
    private static final String ERR_MSG_FMT_EVENT_TYPE_INVALID_FOR_CA_LOT =
            "Assessment event type [%s] invalid for CA [%s], Lot [%s]";
    
    //Added by RoweIT for Tenders API integration
    private static final String ADDITIONAL_INFO_PROCUREMENT_ROUTE = "Procurement Route";  
    private static final String ADDITIONAL_INFO_PROCUREMENT_ROUTE_TYPE = "5";  
    
    // Jaggaer tender status values
    private static final String JAGGAER_STATUS_TO_BE_PUBLISHED = "To Be Published";
    private static final String JAGGAER_STATUS_RUNNING = "Running";
    private static final String JAGGAER_STATUS_TO_BE_EVALUATED = "To Be Evaluated";
    private static final String JAGGAER_STATUS_TECHNICAL_EVALUATION = "Technical Evaluation";
    private static final String JAGGAER_STATUS_COMMERCIAL_EVALUATION = "Commercial Evaluation";
    private static final String JAGGAER_STATUS_FINAL_EVALUATION = "Final Evaluation";
    private static final String JAGGAER_STATUS_CLOSED = "Closed";
    private static final String JAGGAER_STATUS_FINAL_EVALUATION_PRE_AWARDED = "Final Evaluation - Pre-Awarded";
    private static final String JAGGAER_STATUS_AWARDED = "Awarded";
    private static final String JAGGAER_STATUS_AWARDED_TO_OFFLINE_RESPONSE = "Awarded to Offline Response";
    private static final String JAGGAER_STATUS_NOT_AWARDED = "Not Awarded";
    private static final String JAGGAER_STATUS_ROUND_CREATED_IN_NEW_RFQ = "Round Created in New RFQ";
    private static final String JAGGAER_STATUS_SUSPENDED = "Suspended";
    private static final String JAGGAER_STATUS_ENDED = "Ended";

    
    // Jaggaer tender status codes
	private static final String JAGGAER_STATUS_CODE_TO_BE_PUBLISHED = "0";
	private static final String JAGGAER_STATUS_CODE_RUNNING = "300";
	private static final String JAGGAER_STATUS_CODE_TO_BE_EVALUATED = "400";
	private static final String JAGGAER_STATUS_CODE_TECHNICAL_EVALUATION = "800";
    private static final String JAGGAER_STATUS_CODE_COMMERCIAL_EVALUATION = "900";
    private static final String JAGGAER_STATUS_CODE_FINAL_EVALUATION = "950";
    private static final String JAGGAER_STATUS_CODE_CLOSED = "1200";
    private static final String JAGGAER_STATUS_CODE_FINAL_EVALUATION_PRE_AWARDED = "975";
    private static final String JAGGAER_STATUS_CODE_AWARDED = "500";
    private static final String JAGGAER_STATUS_CODE_AWARDED_TO_OFFLINE_RESPONSE = "50";
    private static final String JAGGAER_STATUS_CODE_NOT_AWARDED = "700";
    private static final String JAGGAER_STATUS_CODE_ROUND_CREATED_IN_NEW_RFQ = "1800";
    private static final String JAGGAER_STATUS_CODE_SUSPENDED = "1100";
    private static final String JAGGAER_STATUS_CODE_ENDED = "1500";


    private final UserProfileService userProfileService;
    private final CriteriaService criteriaService;
    private final OcdsConfig ocdsConfig;
    private final RetryableTendersDBDelegate retryableTendersDBDelegate;
    private final TendersAPIModelUtils tendersAPIModelUtils;
    private final ValidationService validationService;
    private final SupplierService supplierService;
    private final DocumentConfig documentConfig;
    private final AssessmentService assessmentService;
    private final DocumentUploadService documentUploadService;
    private final DocumentTemplateService dTemplateService;

    // TODO: switch remaining direct Jaggaer calls to use jaggaerService
    private final JaggaerAPIConfig jaggaerAPIConfig;
    private final ExperimentalFlagsConfig experimentalFlags;
    private final JaggaerService jaggaerService;
    private final ConclaveService conclaveService;

    private final AgreementsService agreementsService;
    private final AwardService awardService;

    private final AsyncExecutor asyncExecutor;
    private final SupplierStoreFactory supplierStoreFactory;
    private final EventTransitionService eventTransitionService;

    private final ExecutorService jaggerUploadExecutorService = Executors.newFixedThreadPool(10);
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);

    /**
     * Creates a Jaggaer Rfx (CCS 'Event' equivalent). Will use {@link Tender#getTitle()} for the
     * event name, if specified, otherwise falls back on the default event title logic (using the
     * project name).
     * <p>
     * Creates with a default event type of 'TBD'.
     *
     * @param projectId             CCS project id
     * @param createEvent           wraps non-OCDS and OCDS details of the event
     * @param downSelectedSuppliers will default to FALSE if null
     * @param principal
     * @return
     */
    @Transactional
    public EventSummary createEvent(final Integer projectId, final CreateEvent createEvent,
                                    Boolean downSelectedSuppliers, final String principal) {
        boolean twoStageEvent = false;
        boolean scheduleSupplierSync = false;

        // Get project from tenders DB to obtain Jaggaer project id
        var project = retryableTendersDBDelegate.findProcurementProjectById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project '" + projectId + "' not found"));

        // check any valid events existed before
        Optional<ProcurementEvent> existingEventOptional = CollectionUtils.isNotEmpty(project.getProcurementEvents()) ? getExistingValidEvents(project.getProcurementEvents()) : Optional.empty();


        if (!existingEventOptional.isPresent()) {
            log.info("No events exists for this project");
        } else {
            TwoStageEventService twoStageEventService = new TwoStageEventService();
            // complete the event and copy suppliers
            var existingEvent = existingEventOptional.get();
            twoStageEvent = twoStageEventService.isTwoStageEvent(createEvent, existingEvent);
            if (!twoStageEvent) {
                eventTransitionService.completeExistingEvent(existingEvent, principal);
            } else {
                twoStageEventService.markComplete(retryableTendersDBDelegate, existingEvent);
            }
        }

        // Set defaults if no values supplied
        var createEventNonOCDS = requireNonNullElse(createEvent.getNonOCDS(), new CreateEventNonOCDS());
        var createEventOCDS = requireNonNullElse(createEvent.getOCDS(), new CreateEventOCDS());
        var eventTypeValue = ofNullable(createEventNonOCDS.getEventType())
                .map(DefineEventType::getValue).orElseGet(ViewEventType.TBD::getValue);

        downSelectedSuppliers = requireNonNullElse(downSelectedSuppliers, Boolean.FALSE);

        var eventName = StringUtils.hasText(createEventOCDS.getTitle()) ? createEventOCDS.getTitle()
                : getDefaultEventTitle(project.getProjectName(), eventTypeValue);

        var eventBuilder = ProcurementEvent.builder();
        //setting true by default, need to revisit
        eventBuilder.refreshSuppliers(true);
        // Optional return values
        Integer returnAssessmentId = null;
        String rfxReferenceCode = null;
        ExportRfxResponse exportRfxResponse = null;

        if (createEventNonOCDS.getEventType() != null
                && ASSESSMENT_EVENT_TYPES.contains(createEventNonOCDS.getEventType())) {

            // Either create a new assessment or validate and link to existing one
            if (createEvent.getNonOCDS().getAssessmentId() == null) {
                var newAssessmentId = assessmentService.createEmptyAssessment(project.getCaNumber(),
                        project.getLotNumber(), createEventNonOCDS.getEventType(), principal);
                eventBuilder.assessmentId(newAssessmentId);

                var validatedAssessment = assessmentService.getAssessment(
                        newAssessmentId, Boolean.FALSE, Optional.empty());

                setRefreshSuppliersForEvent(eventBuilder, validatedAssessment);

                returnAssessmentId = newAssessmentId;
                log.debug("Created new empty assessment: {}", newAssessmentId);
            } else {
                var validatedAssessment = assessmentService.getAssessment(
                        createEvent.getNonOCDS().getAssessmentId(), Boolean.FALSE, Optional.empty());
                eventBuilder.assessmentId(validatedAssessment.getAssessmentId());
                setRefreshSuppliersForEvent(eventBuilder, validatedAssessment);
                returnAssessmentId = validatedAssessment.getAssessmentId();
                log.debug("Linking existing assessment: {} to new event",
                        validatedAssessment.getAssessmentId());
            }
        }

        if (!TENDER_DB_ONLY_EVENT_TYPES.contains(ViewEventType.fromValue(eventTypeValue))) {
//      List<Supplier> suppliers = getSuppliers(project, existingEventOptional.orElse(null), eventTypeValue, twoStageEvent);
            scheduleSupplierSync = true;
            var createUpdateRfx = createRfxRequest(project, eventName, principal, null);

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
            exportRfxResponse = getSingleRfx(createRfxResponse.getRfxId());
        }

        // Persist the Jaggaer Rfx details as a new event in the tenders DB
        var ocdsAuthority = ocdsConfig.getAuthority();
        var ocidPrefix = ocdsConfig.getOcidPrefix();

        var tenderStatus = TenderStatus.PLANNING.getValue();

        if (exportRfxResponse != null && exportRfxResponse.getRfxSetting() != null) {
            var rfxStatus = jaggaerAPIConfig.getRfxStatusAndEventTypeToTenderStatus()
                    .get(exportRfxResponse.getRfxSetting().getStatusCode());

            tenderStatus = rfxStatus != null && rfxStatus.get(eventTypeValue) != null
                    ? rfxStatus.get(eventTypeValue).getValue()
                    : null;
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

        if (null != createEvent.getNonOCDS() && null != createEvent.getNonOCDS().getTemplateGroupId()) {
            eventBuilder.templateId(createEvent.getNonOCDS().getTemplateGroupId().intValue());
        }

        if(CollectionUtils.isNotEmpty(project.getProcurementEvents())){
            setRefreshSuppliersForEvent(eventBuilder, project.getProcurementEvents());
        }


        Integer existingEventId = existingEventOptional.isPresent() ? existingEventOptional.get().getId() : null;


        if(!Objects.isNull(existingEventId)  ){

            if(COMPLETE_EVENT_TYPES.contains(ViewEventType.fromValue(existingEventOptional.get().getEventType()))){
                eventBuilder.refreshSuppliers(true);
            }else{
                eventBuilder.refreshSuppliers(false);
            }

        }


        var event = eventBuilder.build();
        ProcurementEvent procurementEvent;

        // If event is an AssessmentType - add suppliers to Tenders DB (as no event exists in Jaggaer)
        if (!twoStageEvent && createEventNonOCDS.getEventType() != null
                && ASSESSMENT_EVENT_TYPES.contains(createEventNonOCDS.getEventType())) {
            procurementEvent = addSuppliersToTendersDB(event,
                    supplierService.getSuppliersForLot(project.getCaNumber(), project.getLotNumber()), true,
                    principal);
        } else {
            procurementEvent = retryableTendersDBDelegate.save(event);
        }

        if (scheduleSupplierSync) {
            JaggaerSupplierEventData eventData = new JaggaerSupplierEventData(project.getId(), procurementEvent.getId(), eventTypeValue, existingEventId, twoStageEvent, true);
            List<Supplier> suppliers = getSuppliers(project, existingEventOptional.orElse(null), eventTypeValue, twoStageEvent);
            if (null != suppliers && suppliers.size() > 0) {
                if (suppliers.size() > experimentalFlags.getAsyncJaggaerSupplierCountThreshold()) {
                    asyncExecutor.submit(principal, JaggaerSupplierPush.class, eventData, "ProcurementEvent", String.valueOf(procurementEvent.getId()));
                } else {
                    eventData.setSuppliers(suppliers);
                    asyncExecutor.execute(principal, JaggaerSupplierPush.class, eventData);
                }
            }
        }

        return tendersAPIModelUtils.buildEventSummary(procurementEvent.getEventID(), eventName,
                Optional.ofNullable(rfxReferenceCode), ViewEventType.fromValue(eventTypeValue),
                TenderStatus.PLANNING, EVENT_STAGE, Optional.ofNullable(returnAssessmentId));
    }

    private void setRefreshSuppliersForEvent(ProcurementEvent.ProcurementEventBuilder eventBuilder, Set<ProcurementEvent> procurementEvents) {

        Optional<ProcurementEvent>  downSelectedProcurementEvent=procurementEvents.stream().filter(event -> !isClosedStatus(event.getTenderStatus())).filter(ProcurementEvent::getDownSelectedSuppliers).findFirst();
        if(downSelectedProcurementEvent.isPresent()){
            eventBuilder.refreshSuppliers(false);
        }

    }

    private void setRefreshSuppliersForEvent(ProcurementEvent.ProcurementEventBuilder eventBuilder, Assessment validatedAssessment) {
        Optional<AssessmentTool> assesmentToolOptional=retryableTendersDBDelegate.findAssessmentToolByExternalToolId(validatedAssessment.getExternalToolId());
        if(assesmentToolOptional.isPresent()){
            if(assesmentToolOptional.get().getDownSelectSuppliers()){
                eventBuilder.refreshSuppliers(false);
            }
        }
    }
    private void setRefreshSuppliersForEvent(ProcurementEvent  event, Assessment validatedAssessment) {
        Optional<AssessmentTool> assesmentToolOptional=retryableTendersDBDelegate.findAssessmentToolByExternalToolId(validatedAssessment.getExternalToolId());
        if(assesmentToolOptional.isPresent()){
            if(assesmentToolOptional.get().getDownSelectSuppliers()){
                event.setRefreshSuppliers(false);
            }
        }
    }




    public List<Supplier> getSuppliers(ProcurementProject project, ProcurementEvent existingEvent,
                                       String eventTypeValue, boolean twoStageEvent) {

        if (null != existingEvent) {
          //  if (existingEvent.isTendersDBOnly() || twoStageEvent) {
                SupplierStore supplierStore = supplierStoreFactory.getStore(existingEvent);
                return supplierStore.getSuppliers(existingEvent);
          //  }
        }

        if (ViewEventType.TBD.equals(ViewEventType.fromValue(eventTypeValue))) {
            //get suppliers
            var lotSuppliersOrgIds = agreementsService.getLotSuppliers(project.getCaNumber(), project.getLotNumber())
                    .stream().map(lotSupplier -> lotSupplier.getOrganization().getId())
                    .collect(Collectors.toSet());

            return retryableTendersDBDelegate
                    .findOrganisationMappingByCasOrganisationIdIn(lotSuppliersOrgIds).stream().map(org -> {
                        var companyData = CompanyData.builder().id(org.getExternalOrganisationId()).build();
                        return Supplier.builder().companyData(companyData).build();
                    }).collect(Collectors.toList());

        }
        return null;
    }

    private ProcurementEvent getExistingValidEventForProject(final Integer projectId) {
        // Find a list of any existing, valid events for this project and return the first found (as there should never be more than one)
        ProcurementProject project = retryableTendersDBDelegate.findProcurementProjectById(projectId).orElseThrow(() -> new ResourceNotFoundException("Project '" + projectId + "' not found"));

        Optional<ProcurementEvent> existingEventOptional = CollectionUtils.isNotEmpty(project.getProcurementEvents()) ? getExistingValidEvents(project.getProcurementEvents()) : Optional.empty();

        if (existingEventOptional.isPresent()) {
            return existingEventOptional.get();
        }

        return null;
    }

    public Boolean isLotIdentifiedForGcloudAssessment(final Integer projectId) {
        // We assume that the lot is not identified unless the assessment status is complete
        Boolean isLotIdentified = false;

        // To determine the assessment status, first get the first valid event
        ProcurementEvent validEvent = getExistingValidEventForProject(projectId);

        if (validEvent != null && validEvent.getAssessmentId() != null) {
            // Now use the assessment ID against this event to fetch the Gcloud assessment
            Optional<GCloudAssessmentEntity> optionalAssessment = retryableTendersDBDelegate.findGcloudAssessmentById(validEvent.getAssessmentId());

            if (optionalAssessment.isPresent()) {
                // We've got the GCloud assessment, so now just check its status and set the boolean accordingly
                GCloudAssessmentEntity assessment = optionalAssessment.get();

                if (assessment.getStatus() == AssessmentStatusEntity.COMPLETE) {
                    isLotIdentified = true;
                }
            }
        }

        return isLotIdentified;
    }

    private Optional<ProcurementEvent> getExistingValidEvents(Set<ProcurementEvent> procurementEvents) {

        return procurementEvents.stream().filter(event -> !isClosedStatus(event.getTenderStatus())).findFirst();

    }

    private boolean isClosedStatus(String tenderStatus) {
        if (null == tenderStatus)
            return false;
        return CLOSED_STATUS_LIST.contains(tenderStatus.toLowerCase());
    }

    /**
     * Create Jaggaer request object.
     */
    private CreateUpdateRfx createRfxRequest(final ProcurementProject project, final String eventName,
                                             final String principal, final List<Supplier> suppliers) {

        // Fetch Jaggaer ID and Buyer company ID from Jaggaer profile based on OIDC login id
        var jaggaerUserId = userProfileService.resolveBuyerUserProfile(principal)
                .orElseThrow(() -> new AuthorisationFailureException(ERR_MSG_JAGGAER_USER_NOT_FOUND))
                .getUserId();
        var jaggaerBuyerCompanyId = userProfileService.resolveBuyerUserCompany(principal).getBravoId();

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

        var suppliersList = SuppliersList.builder().supplier(suppliers).build();
        var rfx = Rfx.builder().rfxSetting(rfxSetting)
            .rfxAdditionalInfoList(new RfxAdditionalInfoList(
                Arrays.asList(additionalInfoFramework, additionalInfoLot)))
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
        var exportRfxResponse = jaggaerService.getSingleRfx(event.getExternalEventId());

        return tendersAPIModelUtils.buildEventDetail(exportRfxResponse.getRfxSetting(), event,
                event.isDataTemplateEvent() ? criteriaService.getEvalCriteria(projectId, eventId, true)
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
    @Transactional
    public EventSummary updateProcurementEvent(final Integer procId, final String eventId,
                                               final UpdateEvent updateEvent, final String principal) {

        log.debug("Update Event {}", updateEvent);

        var event = validationService.validateProjectAndEventIds(procId, eventId);
        var exportRfxResponse = getSingleRfx(event.getExternalEventId());

        if (updateEvent.getEventType() != null) {
            // validate different rules before update
            validationService.validateEventTypeBeforeUpdate(exportRfxResponse,
                    updateEvent.getEventType().getValue());
        }
        validationService.validateUpdateEventAssessment(updateEvent, event, principal);

        // event is ABANDONED AND no new event is created THEN project = closed
        if (validationService.isEventAbandoned(exportRfxResponse, updateEvent.getEventType())) {
            var procurementEvents = retryableTendersDBDelegate.findProcurementEventsByProjectId(procId);
            if (procurementEvents != null && procurementEvents.size() == 1) {


                eventTransitionService.terminateEvent(procId, eventId, TerminationType.CANCELLED, principal, true);
            }
        }
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
                if (isAssessmentToolPresent(event, updateEvent.getEventType())) {
                    createAssessment = true;
                }
            }

            event.setEventType(updateEvent.getEventType().getValue());
            updateDB = true;
        }

        // Valid to supply either for an existing event
        if (updateEvent.getAssessmentId() != null
                || updateEvent.getAssessmentSupplierTarget() != null) {
            //TODO whether supplierTarget Null check required?
            event.setAssessmentSupplierTarget(updateEvent.getAssessmentSupplierTarget());
            if (null != updateEvent.getAssessmentId())
                event.setAssessmentId(updateEvent.getAssessmentId());
            updateDB = true;
        }

        // Create a new empty assessment
        if (createAssessment) {
            returnAssessmentId = assessmentService.createEmptyAssessment(event.getProject().getCaNumber(),
                    event.getProject().getLotNumber(), updateEvent.getEventType(), principal);


            var validatedAssessment = assessmentService.getAssessment(
                    returnAssessmentId, Boolean.FALSE, Optional.empty());

            setRefreshSuppliersForEvent(event, validatedAssessment);


        } else if (updateEvent.getAssessmentId() != null) {
            // Return the existing (validated) assessmentId
            returnAssessmentId = updateEvent.getAssessmentId();

            var validatedAssessment = assessmentService.getAssessment(
                    returnAssessmentId, Boolean.FALSE, Optional.empty());

            setRefreshSuppliersForEvent(event, validatedAssessment);
        }

        // Save to Jaggaer
        if (updateJaggaer) {
            jaggaerService.createUpdateRfx(rfx, OperationCode.CREATEUPDATE);
        }

        exportRfxResponse = getSingleRfx(event.getExternalEventId());

        // Save to Tenders DB
        if (updateDB) {

            var tenderStatus = TenderStatus.PLANNING.getValue();
            if (exportRfxResponse.getRfxSetting() != null) {
                var rfxStatus = jaggaerAPIConfig.getRfxStatusAndEventTypeToTenderStatus()
                        .get(exportRfxResponse.getRfxSetting().getStatusCode());

                tenderStatus = rfxStatus != null && rfxStatus.get(event.getEventType()) != null
                        ? rfxStatus.get(event.getEventType()).getValue()
                        : null;
            }

            if (null != updateEvent.getTemplateGroupId()) {
                if (null == event.getProcurementTemplatePayload())
                    event.setTemplateId(updateEvent.getTemplateGroupId().intValue());
            }

            event.setUpdatedAt(Instant.now());
            event.setUpdatedBy(principal);
            if (null != returnAssessmentId)
                event.setAssessmentId(returnAssessmentId);
            if (exportRfxResponse.getRfxSetting().getPublishDate() != null) {
                event.setPublishDate(exportRfxResponse.getRfxSetting().getPublishDate().toInstant());
            }
            if (exportRfxResponse.getRfxSetting().getCloseDate() != null) {
                event.setCloseDate(exportRfxResponse.getRfxSetting().getCloseDate().toInstant());
            }

            if (tenderStatus != null) {
                event.setTenderStatus(tenderStatus);
            }
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
     * check assessment tool id is present
     *
     * @param event
     * @param eventType
     * @return boolean
     */
    private boolean isAssessmentToolPresent(ProcurementEvent event, DefineEventType eventType) {
        var lotEventType = agreementsService
                .getLotEventTypes(event.getProject().getCaNumber(), event.getProject().getLotNumber())
                .stream().filter(let -> eventType.name().equals(let.getType())).findFirst()
                .orElseThrow(() -> new ValidationException(format(ERR_MSG_FMT_EVENT_TYPE_INVALID_FOR_CA_LOT,
                        eventType, event.getProject().getCaNumber(), event.getProject().getLotNumber())));
        return Objects.nonNull(lotEventType.getAssessmentToolId());
    }

    /**
     * Get all suppliers on an event.
     *
     * @param procId
     * @param eventId
     * @return
     */
    public EventSuppliers getSuppliers(final Integer procId, final String eventId) {

        log.debug("Get suppliers for event '{}'", eventId);

        var event = validationService.validateProjectAndEventIds(procId, eventId);

        SupplierStore supplierStore = supplierStoreFactory.getStore(event);

        return supplierStore.getSuppliers(event, null);

//    if (event.isTendersDBOnly()) {
//      log.debug("Event {} is retrieved from Tenders DB only {}", event.getId(),
//          event.getEventType());
//      return getSuppliersFromTendersDB(event);
//    }
//    log.debug("Event {} is retrieved from Jaggaer {}", event.getId(), event.getEventType());
//    return getSuppliersFromJaggaer(event);
    }

    /**
     * Get the supplier info for given Id on an event.
     *
     * @param procId
     * @param eventId
     * @param eventId
     * @return
     */
    public OrganizationReference1 getSupplierInfo(final Integer procId, final String eventId, final String supplierId) {

        log.debug("Get suppliers for event '{}'", eventId);
        var event = validationService.validateProjectAndEventIds(procId, eventId);

        log.debug("Event {} is retrieved from Jaggaer {}", event.getId(), event.getEventType());
        return getSupplierInfoFromJaggaer(supplierId);

    }

    /**
     * Add/Overwrite suppliers on an Event.
     * <p>
     * If it is an Assessment Event Type, suppliers will only be added in the Tenders DB, otherwise
     * updates will only be in Jaggaer.
     *
     * @param procId
     * @param eventId
     * @param eventSuppliers
     * @param overwrite      if <code>true</code> will replace the list of suppliers, otherwise it will
     *                       just add to the list.
     * @return
     */
    @Transactional
    public EventSuppliers addSuppliers(final Integer procId, final String eventId,
                                       final EventSuppliers eventSuppliers, final boolean overwrite, final String principal) {

        var event = validationService.validateProjectAndEventIds(procId, eventId);

        SupplierStore supplierStore = supplierStoreFactory.getStore(event);

        // we are setting refresh suppliers to false
        event.setRefreshSuppliers(false);
        retryableTendersDBDelegate.save(event);

        return supplierStore.storeSuppliers(event, eventSuppliers, principal);
//
//    var supplierOrgIds = eventSuppliers.getSuppliers().stream().map(OrganizationReference1::getId)
//        .collect(Collectors.toSet());
//
//    var supplierOrgMappings =
//        retryableTendersDBDelegate.findOrganisationMappingByOrganisationIdIn(supplierOrgIds);
//
//    // Validate suppliers exist in Organisation Mapping Table
//    if (supplierOrgMappings.size() != eventSuppliers.getSuppliers().size()) {
//
//      var missingSuppliers = new ArrayList<String>();
//      eventSuppliers.getSuppliers().stream().forEach(or -> {
//        if (supplierOrgMappings.parallelStream()
//            .filter(som -> som.getOrganisationId().equals(or.getId())).findFirst().isEmpty()) {
//          missingSuppliers.add(or.getId());
//        }
//      });
//
//      if (!missingSuppliers.isEmpty()) {
//        throw new ResourceNotFoundException(String.format(
//            "The following suppliers are not present in the Organisation Mappings, so unable to add them: %s",
//            missingSuppliers));
//      }
//    }
//
//    if (eventSuppliers.getJustification() != null) {
//      event.setSupplierSelectionJustification(eventSuppliers.getJustification());
//    }
//    /*
//     * If Event is a Tenders DB only type, suppliers are stored in the Tenders DB only, otherwise
//     * they are stored in Jaggaer.
//     */
//    if (event.isTendersDBOnly()) {
//      log.debug("Event {} is persisted in Tenders DB only {}", event.getEventID(),
//          event.getEventType());
//      var assessment =
//          assessmentService.getAssessment(event.getAssessmentId(), Boolean.FALSE, Optional.empty());
//      var dimensionWeightingCheck = assessment.getDimensionRequirements().stream()
//          .map(DimensionRequirement::getWeighting).reduce(0, Integer::sum);
//      if (dimensionWeightingCheck != 100) {
//        throw new ValidationException(ERR_MSG_ALL_DIMENSION_WEIGHTINGS);
//      }
//      addSuppliersToTendersDB(event, supplierOrgMappings, overwrite, principal);
//    } else {
//      log.debug("Event {} is persisted in Jaggaer {}", event.getId(), event.getEventType());
//      addSuppliersToJaggaer(event, supplierOrgMappings, overwrite);
//    }
//
//    return eventSuppliers;
    }

    /**
     * Delete a supplier from an event.
     * <p>
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

        SupplierStore supplierStore = supplierStoreFactory.getStore(event);

        supplierStore.deleteSupplier(event, organisationId, principal);
//
//    // Determine Jaggaer supplier id
//    var om = retryableTendersDBDelegate.findOrganisationMappingByOrganisationId(organisationId)
//        .orElseThrow(() -> new IllegalArgumentException(
//            String.format(ERR_MSG_FMT_SUPPLIER_NOT_FOUND, organisationId)));
//
//    /*
//     * If Event is a Tenders DB only type, suppliers are stored in the Tenders DB only, otherwise
//     * they are stored in Jaggaer.
//     */
//    if (event.isTendersDBOnly()) {
//      log.debug("Event {} is persisted in Tenders DB only {}", event.getId(), event.getEventType());
//      deleteSupplierFromTendersDB(event, om, principal);
//    } else {
//      log.debug("Event {} is persisted in Jaggaer {}", event.getId(), event.getEventType());
//      deleteSupplierFromJaggaer(event, om);
//    }
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
     * Returns a list of document attachments at the event level.
     *
     * @param procId
     * @param eventId
     * @return
     */
    @Transactional
    public ResponseSummary getSupplierResponses(final Integer procId, final String eventId) {

        var procurementEvent = validationService.validateProjectAndEventIds(procId, eventId);
        var exportRfxResponse = jaggaerService.getRfxWithSuppliersOffersAndResponseCounters(procurementEvent.getExternalEventId());

        final var lastRound = exportRfxResponse.getSupplierResponseCounters().getLastRound();
        var responseSummary = new ResponseSummary().invited(lastRound.getNumSupplInvited())
                .responded(lastRound.getNumSupplResponded()).noResponse(lastRound.getNumSupplNotResponded())
                .declined(lastRound.getNumSupplRespDeclined());

        return responseSummary.responders(exportRfxResponse.getSuppliersList().getSupplier().stream()
                .map(supplier -> this.convertToResponders(supplier,
                        getSupplierResponseDate(exportRfxResponse.getOffersList(), supplier)))
                .collect(Collectors.toList()));
    }

    private OffsetDateTime getSupplierResponseDate(final OffersList offerList,
                                                   final Supplier supplier) {
        if (Objects.nonNull(offerList) && Objects.nonNull(offerList.getOffer())) {
            var respondedSupplier = offerList.getOffer().stream()
                    .filter(offer -> offer.getSupplierId().equals(supplier.getCompanyData().getId()))
                    .findFirst();
            if (respondedSupplier.isPresent()) {
                return respondedSupplier.get().getLastUpdateDate();
            }
        }
        // it won' happen in ideal case but to be safe side
        return null;
    }

    private Responders convertToResponders(final Supplier supplier,
                                           final OffsetDateTime respondedDateTime) {
        var organisationMapping = retryableTendersDBDelegate
                .findOrganisationMappingByExternalOrganisationId(supplier.getCompanyData().getId())
                .orElseThrow(() -> new TendersDBDataException(
                        String.format(ERR_MSG_FMT_SUPPLIER_NOT_FOUND, supplier.getCompanyData().getId())));

        return new Responders()
                .supplier(new OrganizationReference1().id(organisationMapping.getCasOrganisationId())
                        .name(supplier.getCompanyData().getName()))
                .responseState(!RESPONSE_STATES.contains(supplier.getStatus().trim())
                        ? supplier.getStatusCode() == -2 ? Responders.ResponseStateEnum.DECLINED : Responders.ResponseStateEnum.SUBMITTED
                        : Responders.ResponseStateEnum.DRAFT)
                .readState(!RESPONSE_STATES.contains(supplier.getStatus().trim()) ? Responders.ReadStateEnum.READ
                        : Responders.ReadStateEnum.UNREAD)
                .responseDate(respondedDateTime);
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

        var jaggaerUserId = userProfileService.resolveBuyerUserProfile(principal)
                .orElseThrow(() -> new AuthorisationFailureException(ERR_MSG_JAGGAER_USER_NOT_FOUND))
                .getUserId();

        var procurementEvent = validationService.validateProjectAndEventIds(procId, eventId);
        var exportRfxResponse = getRfxWithSuppliers(procurementEvent.getExternalEventId());
        var status = jaggaerAPIConfig.getRfxStatusToTenderStatus()
                .get(exportRfxResponse.getRfxSetting().getStatusCode());

        if (TenderStatus.PLANNED != status) {
            throw new IllegalArgumentException(
                    "You cannot publish an event unless it is in a 'planned' state");
        }

        if(procurementEvent.getRefreshSuppliers()){
            jaggaerSupplierRefresh(procId, eventId, principal, procurementEvent, exportRfxResponse);
        }

        retrieveAndUploadDocuments(principal, procurementEvent);
        validationService.validatePublishDates(publishDates);
        jaggaerService.publishRfx(procurementEvent, publishDates, jaggaerUserId);
        // after publish get rfx details and update tender status, publish date and close date
        updateStatusAndDates(principal, procurementEvent);
    }

    public void jaggaerSupplierRefresh(Integer procId, String eventId, String principal, ProcurementEvent procurementEvent, ExportRfxResponse exportRfxResponse) {
        Set<OrganisationMapping> agreementSuppliers = supplierService.getSuppliersForLot(procurementEvent.getProject().getCaNumber(), procurementEvent.getProject().getLotNumber());
        List<Supplier> jaggaerSuppliers = Objects.nonNull(exportRfxResponse.getSuppliersList()) ? exportRfxResponse.getSuppliersList().getSupplier() : new ArrayList();

        Set<Integer> agreementSupplierIds = agreementSuppliers.stream().map(OrganisationMapping::getExternalOrganisationId).collect(Collectors.toSet());
        Set<Integer> jaggaerSupplierIds = jaggaerSuppliers.stream().map(supplier -> supplier.getCompanyData().getId()).collect(Collectors.toSet());

        boolean agreementHasSuperSet = agreementSupplierIds.containsAll(jaggaerSupplierIds);
        boolean jaggaerHasSuperSet = jaggaerSupplierIds.containsAll(agreementSupplierIds);

        if (!agreementHasSuperSet || !jaggaerHasSuperSet) {
            if (agreementHasSuperSet && !jaggaerHasSuperSet) {
                log.debug("computing extra suppliers from agreement serivce/jaggaer {}/{}", agreementSupplierIds.size(), jaggaerSupplierIds.size());
                agreementSupplierIds.removeAll(jaggaerSupplierIds);
                log.debug("{} suppliers are identified to be added in Jaggaer", agreementSupplierIds.size());
                List<OrganisationMapping> newAggrementSuppliers = agreementSuppliers.stream()
                        .filter(organisationMapping -> agreementSupplierIds.contains(organisationMapping.getExternalOrganisationId()))
                        .collect(Collectors.toList());

                EventSuppliers newEventSuppliers = createNewEventSuppliers(newAggrementSuppliers);
                addSuppliers(procId, eventId, newEventSuppliers, false, principal);
            } else {
                log.debug("{} suppliers will be re-pushed to Jaggaer", agreementSuppliers.size());
                List<OrganisationMapping> newAggrementSuppliers = agreementSuppliers.stream().collect(Collectors.toList());
                EventSuppliers newEventSuppliers = createNewEventSuppliers(newAggrementSuppliers);
                newEventSuppliers.setOverwriteSuppliers(Boolean.TRUE);
                addSuppliers(procId, eventId, newEventSuppliers, true, principal);
            }
        }else{
            log.info("No change in suppliers detected, suppliers will not be refreshed in Jaggaer {}/{}", agreementSuppliers.size(), jaggaerSuppliers.size());
        }
    }

    private EventSuppliers createNewEventSuppliers(List<OrganisationMapping> newAggrementSuppliers) {

        List<OrganizationReference1> eventSuppliersOrgs= newAggrementSuppliers.stream().map(organisationMapping -> {

            OrganizationReference1 organizationReference1= new OrganizationReference1();
            organizationReference1.setId(organisationMapping.getCasOrganisationId());
            return organizationReference1;
        }).collect(Collectors.toList());

        EventSuppliers eventSuppliers= new EventSuppliers();

        eventSuppliers.setSuppliers(eventSuppliersOrgs);
        eventSuppliers.setOverwriteSuppliers(false);
        eventSuppliers.setJustification("New Suppliers Added");

        return eventSuppliers;
    }


    public void updateStatusAndDates(
            final String principal, final ProcurementEvent procurementEvent) {

        var exportRfxResponse = getSingleRfx(procurementEvent.getExternalEventId());

        var tenderStatus = TenderStatus.PLANNING.getValue();
        if (exportRfxResponse.getRfxSetting() != null) {
            var rfxStatus =
                    jaggaerAPIConfig
                            .getRfxStatusAndEventTypeToTenderStatus()
                            .get(exportRfxResponse.getRfxSetting().getStatusCode());

            tenderStatus =
                    rfxStatus != null && rfxStatus.get(procurementEvent.getEventType()) != null
                            ? rfxStatus.get(procurementEvent.getEventType()).getValue()
                            : null;
        }

        procurementEvent.setUpdatedAt(Instant.now());
        procurementEvent.setUpdatedBy(principal);
        if (exportRfxResponse.getRfxSetting().getPublishDate() != null) {
            procurementEvent.setPublishDate(
                    exportRfxResponse.getRfxSetting().getPublishDate().toInstant());
        }
        if (exportRfxResponse.getRfxSetting().getCloseDate() != null) {
            procurementEvent.setCloseDate(exportRfxResponse.getRfxSetting().getCloseDate().toInstant());
        } else {
            procurementEvent.setCloseDate(Instant.now());
        }


        if (tenderStatus != null) {
            procurementEvent.setTenderStatus(tenderStatus);
        }
        retryableTendersDBDelegate.save(procurementEvent);
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
        var externalEventIdsAllProjects = events.stream().map(ProcurementEvent::getExternalEventId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<String, RfxSetting> externalIdRfxResponseMap = getAllRfxSettingMap(externalEventIdsAllProjects);

        return events.stream().map(event -> {
            TenderStatus statusCode = null;
            RfxSetting rfxSetting = null;
            if (event.getExternalEventId() == null) {
                var assessment = assessmentService.getAssessment(event.getAssessmentId(), Boolean.FALSE,
                        Optional.empty());
                statusCode = TenderStatus.fromValue(assessment.getStatus().toString().toLowerCase());
            } else {
                rfxSetting = externalIdRfxResponseMap.get(event.getExternalEventId());
                if (Objects.nonNull(rfxSetting)) {
                    statusCode = jaggaerAPIConfig.getRfxStatusToTenderStatus()
                            .get(rfxSetting.getStatusCode());
                }
            }
            var eventSummary = tendersAPIModelUtils.buildEventSummary(event.getEventID(),
                    event.getEventName(), Optional.ofNullable(event.getExternalReferenceId()),
                    ViewEventType.fromValue(event.getEventType()), statusCode, EVENT_STAGE,
                    Optional.ofNullable(event.getAssessmentId()));

            if (null != event.getTemplateId())
                eventSummary.setTemplateGroupId(BigDecimal.valueOf(event.getTemplateId()));

            if (Objects.nonNull(eventSummary)) {
                eventSummary.setDashboardStatus(getDashboardStatus(rfxSetting, event));
            }
            updateTenderPeriod(event, rfxSetting, eventSummary);
            return eventSummary;
        }).collect(Collectors.toList());
    }

    /**
     * retursn all the rfx events for the given list of externalIds
     *
     * @param externalEventIdsAllProjects
     * @return
     */
    private Map<String, RfxSetting> getAllRfxSettingMap(Set<String> externalEventIdsAllProjects) {

        var allRfxs = jaggaerService.searchRFx(externalEventIdsAllProjects);
        return allRfxs.stream().map(ExportRfxResponse::getRfxSetting).collect(Collectors.toMap(RfxSetting::getRfxId, rfxSetting -> rfxSetting));
    }

    private void updateTenderPeriod(ProcurementEvent event, RfxSetting rfxSetting, EventSummary eventSummary) {
        if (Objects.nonNull(rfxSetting)) {

            eventSummary.setTenderPeriod(getTenderPeriod(getInstantFromDate(rfxSetting.getPublishDate()), (eventSummary.getDashboardStatus().equals(DashboardStatus.CLOSED) && null != event.getCloseDate()) ? event.getCloseDate() : getInstantFromDate(rfxSetting.getCloseDate())));

            // there may be possibility where close date is not available from jaggaer
            if (Objects.isNull(eventSummary.getTenderPeriod().getEndDate())) {
                eventSummary.getTenderPeriod().setEndDate(event.getCloseDate() == null ? null : OffsetDateTime.ofInstant(event.getCloseDate(), ZoneId.systemDefault()));
            }
        } else {
            eventSummary.tenderPeriod(getTenderPeriod(
                    (event.getPublishDate() == null) ? null : event.getPublishDate(),
                    (event.getCloseDate() == null) ? null : event.getCloseDate()));
        }


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
            event.getCapabilityAssessmentSuppliers()
                    .removeIf(supplierSelection -> supplierSelection.getId() != null);
        }


        Set<SupplierSelection> supplierSelectionSet = supplierOrgMappings.stream().map(org -> {
            return SupplierSelection.builder().organisationMapping(org).procurementEvent(event)
                    .createdAt(Instant.now()).createdBy(principal).build();
        }).collect(Collectors.toSet());
        if (Objects.nonNull(event.getCapabilityAssessmentSuppliers())) {
            event.getCapabilityAssessmentSuppliers().addAll(supplierSelectionSet);
        } else {
            event.setCapabilityAssessmentSuppliers(supplierSelectionSet);
        }

        return retryableTendersDBDelegate.save(event);
    }

    /**
     * Add/overwrite suppliers in Jaggaer.
     *
     * @param event
     * @param supplierOrgMappings
     * @param overwrite
     */
//  private void addSuppliersToJaggaer(final ProcurementEvent event,
//      final Set<OrganisationMapping> supplierOrgMappings, final boolean overwrite) {
//
//    OperationCode operationCode;
//    if (overwrite) {
//      operationCode = OperationCode.UPDATE_RESET;
//    } else {
//      operationCode = OperationCode.CREATEUPDATE;
//    }
//
//    var suppliersList = supplierOrgMappings.stream().map(org -> {
//      var companyData = CompanyData.builder().id(org.getExternalOrganisationId()).build();
//      return Supplier.builder().companyData(companyData).build();
//    }).collect(Collectors.toList());
//
//    // Build Rfx and update
//    var rfxSetting = RfxSetting.builder().rfxId(event.getExternalEventId())
//        .rfxReferenceCode(event.getExternalReferenceId()).build();
//    var rfx = Rfx.builder().rfxSetting(rfxSetting)
//        .suppliersList(SuppliersList.builder().supplier(suppliersList).build()).build();
//    jaggaerService.createUpdateRfx(rfx, operationCode);
//  }

    /**
     * Get suppliers on an event from Tenders DB.
     *
     * @param event
     * @return
     */
//  private EventSuppliers getSuppliersFromTendersDB(final ProcurementEvent event) {
//
//    var suppliers = event.getCapabilityAssessmentSuppliers().stream().map(s -> {
//      var orgIdentity =
//          conclaveService.getOrganisationIdentity(s.getOrganisationMapping().getCasOrganisationId());
//
//      var orgRef = new OrganizationReference1().id(s.getOrganisationMapping().getCasOrganisationId());
//      orgIdentity.ifPresentOrElse(or -> orgRef.name(or.getIdentifier().getLegalName()),
//          () -> log.warn(String.format(ERR_MSG_SUPPLIER_NOT_FOUND_CONCLAVE,
//              s.getOrganisationMapping().getCasOrganisationId())));
//      return orgRef;
//    }).collect(Collectors.toList());
//    return new EventSuppliers().suppliers(suppliers)
//        .justification(event.getSupplierSelectionJustification());
//  }

    /**
     * Get suppliers on an event from Jaggaer.
     *
     * @param event
     * @return
     */
    private EventSuppliers getSuppliersFromJaggaer(final ProcurementEvent event) {

        var existingRfx = jaggaerService.getRfxWithSuppliers(event.getExternalEventId());
        var orgs = new ArrayList<OrganizationReference1>();

        if (existingRfx.getSuppliersList().getSupplier() != null) {
            existingRfx.getSuppliersList().getSupplier().stream().map(s -> {

                var om = retryableTendersDBDelegate
                        .findOrganisationMappingByExternalOrganisationId(s.getCompanyData().getId())
                        .orElseThrow(() -> new IllegalArgumentException(
                                String.format(ERR_MSG_FMT_SUPPLIER_NOT_FOUND, s.getCompanyData().getId())));

                return new OrganizationReference1().id(String.valueOf(om.getCasOrganisationId()))
                        .name(s.getCompanyData().getName());
            }).forEachOrdered(orgs::add);
        }

        return new EventSuppliers().suppliers(orgs)
                .justification(event.getSupplierSelectionJustification());
    }

    /**
     * Get supplier info for given id from Jaggaer.
     *
     * @param supplierId
     * @return
     */
    private OrganizationReference1 getSupplierInfoFromJaggaer(final String supplierId) {

        var companyData = userProfileService.resolveSupplierData(supplierId);

        if (companyData.isPresent()) {
            CompanyInfo companyInfo = companyData.get().getReturnCompanyInfo();

            OrganizationReference1 organizationReference1 = new OrganizationReference1();

            organizationReference1.setId(supplierId);
            organizationReference1.setName(companyInfo.getCompanyName());

            Address1 address = new Address1();
            address.setStreetAddress(companyInfo.getAddress());
            address.setPostalCode(companyInfo.getZip());
            address.setLocality(companyInfo.getCity());
            address.setRegion(companyInfo.getProvince());
            address.setCountryName(companyInfo.getIsoCountry());


            organizationReference1.setAddress(address);
            ContactPoint1 contactPoint1 = new ContactPoint1();
            contactPoint1.setName(companyInfo.getUserSurName());
            contactPoint1.setEmail(companyInfo.getUserEmail());

            contactPoint1.setUrl(
                    org.apache.commons.lang3.StringUtils.isNotEmpty(companyInfo.getWebSite())
                            ? URI.create(companyInfo.getWebSite())
                            : null);

            organizationReference1.setContactPoint(contactPoint1);

            return organizationReference1;
        }
        throw new ResourceNotFoundException(
                String.format("Supplier not found with the given id {}", supplierId));
    }

    /**
     * Delete supplier from Tenders DB.
     *
     * @param event
     * @param supplierOrgMapping
     * @param principal
     */
//  private void deleteSupplierFromTendersDB(final ProcurementEvent event,
//      final OrganisationMapping supplierOrgMapping, final String principal) {
//
//    event.setUpdatedAt(Instant.now());
//    event.setUpdatedBy(principal);
//    retryableTendersDBDelegate.save(event);
//
//    var supplierSelection = event.getCapabilityAssessmentSuppliers().stream()
//        .filter(s -> s.getOrganisationMapping().getId().equals(supplierOrgMapping.getId()))
//        .findFirst().orElseThrow();
//
//    retryableTendersDBDelegate.delete(supplierSelection);
//  }

    /**
     * Delete supplier from Jaggaer.
     *
     * @param event
     */
//  private void deleteSupplierFromJaggaer(final ProcurementEvent event,
//      final OrganisationMapping supplierOrgMapping) {
//
//    // Get all current suppliers on Rfx and remove the one we want to delete
//    var existingRfx = jaggaerService.getRfxWithSuppliers(event.getExternalEventId());
//    List<Supplier> updatedSuppliersList = existingRfx.getSuppliersList().getSupplier().stream()
//        .filter(
//            s -> !s.getCompanyData().getId().equals(supplierOrgMapping.getExternalOrganisationId()))
//        .collect(Collectors.toList());
//    var suppliersList = SuppliersList.builder().supplier(updatedSuppliersList).build();
//
//    // Build Rfx and update
//    var rfxSetting = RfxSetting.builder().rfxId(event.getExternalEventId())
//        .rfxReferenceCode(event.getExternalReferenceId()).build();
//    var rfx = Rfx.builder().rfxSetting(rfxSetting).suppliersList(suppliersList).build();
//    jaggaerService.createUpdateRfx(rfx, OperationCode.UPDATE_RESET);
//  }

    DocumentUpload findDocumentUploadInEvent(final ProcurementEvent event, final String documentId) {
        return event.getDocumentUploads().stream()
                .filter(du -> Objects.equals(du.getDocumentId(), documentId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format(ERR_MSG_FMT_DOCUMENT_NOT_FOUND, documentId)));
    }

    /**
     * Export buyer attachments
     *
     * @param procId
     * @param eventId
     * @param principal
     * @return list of attachments
     */
    @Transactional
    public List<DocumentAttachment> exportDocuments(final Integer procId, final String eventId,
                                                    final String principal) {
        log.debug("Export all Documents from Event {}", eventId);
        var event = validationService.validateProjectAndEventIds(procId, eventId);
        var exportRfxResponse = jaggaerService.getRfxWithWithBuyerAndSellerAttachments(event.getExternalEventId());
        var status = jaggaerAPIConfig.getRfxStatusToTenderStatus()
                .get(exportRfxResponse.getRfxSetting().getStatusCode());
        var attachments = new ArrayList<DocumentAttachment>();

        if (TenderStatus.ACTIVE != status) {
            // Get documents from S3
            event.getDocumentUploads().forEach(doc -> {
                var documentKey = DocumentKey.fromString(doc.getDocumentId());
                var attachment = DocumentAttachment.builder()
                        .data(documentUploadService.retrieveDocument(doc, principal))
                        .fileName(documentKey.getFileName())
                        .contentType(MediaType.parseMediaType(doc.getMimetype())).build();
                attachments.add(attachment);
            });
            // Get draft documents
            dTemplateService.getTemplatesByAgreementAndLot(procId, eventId).forEach(template -> {
                attachments.add(dTemplateService.getDraftDocument(procId, eventId,
                        DocumentKey.fromString(template.getId())));
            });

        } else {
            // Get documents from Jaggaer
            Stream
                    .concat(exportRfxResponse.getBuyerAttachmentsList().getAttachment().stream(),
                            exportRfxResponse.getSellerAttachmentsList().getAttachment().stream())
                    .forEach(doc -> attachments.add(DocumentAttachment
                            .builder().fileName(doc.getFileName()).data(jaggaerService
                                    .getDocument(Integer.valueOf(doc.getFileId()), doc.getFileName()).getData())
                            .build()));
        }
        return attachments;
    }

    /**
     * Extend an Rfx in Jaggaer
     *
     * @param procId
     * @param eventId
     */
    @Transactional
    public CreateUpdateRfxResponse extendEvent(final Integer procId, final String eventId,
                                               final ExtendCriteria extendCriteria, final String principal) {

        userProfileService.resolveBuyerUserProfile(principal)
                .orElseThrow(() -> new AuthorisationFailureException(ERR_MSG_JAGGAER_USER_NOT_FOUND))
                .getUserId();

        var procurementEvent = validationService.validateProjectAndEventIds(procId, eventId);
        var rfxResponse = getSingleRfx(procurementEvent.getExternalEventId());
        var status = jaggaerAPIConfig.getRfxStatusToTenderStatus()
                .get(rfxResponse.getRfxSetting().getStatusCode());

        if (TenderStatus.ACTIVE != status) {
            throw new IllegalArgumentException(
                    "You cannot extend an event unless it is in a 'active' state");
        }

        validationService.validateEndDate(extendCriteria.getEndDate());
        var rfx = RfxRequest.builder()
                .rfxSetting(RfxSettingRequest.builder().rfxId(rfxResponse.getRfxSetting().getRfxId())
                        .rfxReferenceCode(rfxResponse.getRfxSetting().getRfxReferenceCode())
                        .closeDate(extendCriteria.getEndDate().toInstant()).build())
                .build();
        var response = jaggaerService.extendRfx(rfx, OperationCode.UPDATE);
        // after extend get rfx details and update tender status, publish date and close date
        updateStatusAndDates(principal, procurementEvent);
        return response;
    }


    /**
     * @param procId
     * @param eventId
     * @param profile
     * @return
     */
    @Transactional
    public List<SupplierAttachmentResponse> getSupplierAttachmentResponses(final String profile,
                                                                           final Integer procId, final String eventId) {
        return getSupplierAttachmentResponses(profile, procId, eventId, null);
    }

    /**
     * @param profile
     * @param procId
     * @param eventId
     * @param supplierId
     * @return
     */
    @Transactional
    public SupplierAttachmentResponse getSupplierAttachmentResponse(final String profile,
                                                                    final Integer procId, final String eventId, final String supplierId) {

        // Determine Jaggaer supplier id
        var supplierOrganisationMapping =
                retryableTendersDBDelegate.findOrganisationMappingByCasOrganisationId(supplierId)
                        .orElseThrow(() -> new IllegalArgumentException(
                                String.format(ERR_MSG_FMT_SUPPLIER_NOT_FOUND, supplierId)));

        var supplierAttachmentResponsesList = getSupplierAttachmentResponses(profile, procId, eventId,
                supplierOrganisationMapping.getExternalOrganisationId().toString());

        return supplierAttachmentResponsesList.stream().findFirst().get();
    }

    private List<SupplierAttachmentResponse> getSupplierAttachmentResponses(final String profile,
                                                                            final Integer procId, final String eventId, final String supplierId) {

        var procurementEvent = validationService.validateProjectAndEventIds(procId, eventId);

        var exportRfxResponse = jaggaerService.getRfxWithSuppliersOffersAndResponseCounters(procurementEvent.getExternalEventId());
        var supplierList = exportRfxResponse.getSuppliersList();
        verifyForOffers(eventId, exportRfxResponse);

        var offersWithParameters = getOffersWithSupplierAttachments(exportRfxResponse, supplierId);

        if (CollectionUtils.isEmpty(offersWithParameters)) {

            var buyerUser = userProfileService.resolveBuyerUserProfile(profile)
                    .orElseThrow(() -> new AuthorisationFailureException(JAGGAER_USER_NOT_FOUND));
            
            //SCAT-8514 - Hard fix for DOS
            if (procurementEvent.getProject().getCaNumber().equals("RM1043.8")) {
              jaggaerService.openEnvelope(procurementEvent, buyerUser.getUserId(),
                  EnvelopeType.TECH);
            } else {
              jaggaerService.startEvaluationAndOpenEnvelope(procurementEvent,
                  buyerUser.getUserId());
            }

            // get rfx response after Start Evaluation And Open Envelope called
            exportRfxResponse = jaggaerService.getRfxWithSuppliersOffersAndResponseCounters(procurementEvent.getExternalEventId());
            supplierList = exportRfxResponse.getSuppliersList();
            verifyForOffers(eventId, exportRfxResponse);

            offersWithParameters = getOffersWithSupplierAttachments(exportRfxResponse, supplierId);

            if (CollectionUtils.isEmpty(offersWithParameters)) {
                throw new ResourceNotFoundException(
                        String.format(ERR_MSG_FMT_NO_SUPPLIER_RESPONSES_FOUND, eventId));
            }
        }

        List<SupplierAttachmentResponse> supplierAttachmentResponsesList = new ArrayList<>();
        for (Offer offer : offersWithParameters) {
            var supplierAttachmentResponse =
                    SupplierAttachmentResponse.builder().supplierId(offer.getSupplierId())
                            .supplierName(getSupplierName(supplierList, offer.getSupplierId()))
                            .parameterInfoList(new ArrayList<>()).build();
            var attachmentParametersOnly = offer.getTechOffer().getParameterResponses().getParameter()
                    .stream().filter(parameter -> "ATTACH".equals(parameter.getParameterType()))
                    .collect(Collectors.toList());

            var parameterInfoList = attachmentParametersOnly.stream()
                    .map(parameter -> ParameterInfo.builder().parameterId(parameter.getParameterId())
                            .attachmentInfoList(getAttachmentInfoForParameter(parameter)).build())
                    .collect(Collectors.toList());

            supplierAttachmentResponse.getParameterInfoList().addAll(parameterInfoList);

            supplierAttachmentResponsesList.add(supplierAttachmentResponse);
        }
        return supplierAttachmentResponsesList;
    }

    private void verifyForOffers(final String eventId, final ExportRfxResponse exportRfxResponse) {
        if (!ObjectUtils.allNotNull(exportRfxResponse.getOffersList(),
                exportRfxResponse.getOffersList().getOffer())) {
            throw new ResourceNotFoundException(
                    String.format(ERR_MSG_FMT_NO_SUPPLIER_RESPONSES_FOUND, eventId));
        }
    }

    private List<Offer> getOffersWithSupplierAttachments(final ExportRfxResponse exportRfxResponse,
                                                         final String supplierId) {
        List<Offer> offersWithParameters;

        if (Objects.isNull(supplierId)) {
            offersWithParameters = exportRfxResponse.getOffersList().getOffer().stream()
                    .filter(offer -> null != offer && null != offer.getTechOffer()
                            && null != offer.getTechOffer().getParameterResponses()
                            && null != offer.getTechOffer().getParameterResponses().getParameter()
                            && !offer.getTechOffer().getParameterResponses().getParameter().isEmpty())
                    .collect(Collectors.toList());

        } else {
            offersWithParameters = exportRfxResponse.getOffersList().getOffer().stream()
                    .filter(offer -> null != offer && null != offer.getTechOffer()
                            && null != offer.getTechOffer().getParameterResponses()
                            && null != offer.getTechOffer().getParameterResponses().getParameter()
                            && offer.getSupplierId().intValue() == Integer.parseInt(supplierId)
                            && !offer.getTechOffer().getParameterResponses().getParameter().isEmpty())
                    .collect(Collectors.toList());
        }
        return offersWithParameters;
    }

    public DocumentAttachment downloadAttachment(final Integer attachmentId, final String fileName) {

        var docAttachment = jaggaerService.getDocument(attachmentId, fileName);
        return DocumentAttachment.builder().fileName(fileName)
                .contentType(docAttachment.getContentType()).data(docAttachment.getData()).build();
    }

    private String getSupplierName(final SuppliersList supplierList, final Integer supplierId) {
        var supplierName = supplierList.getSupplier().stream()
                .filter(supplier -> supplier.getCompanyData().getId().equals(supplierId))
                .map(supplier -> supplier.getCompanyData().getName()).findFirst();
        return supplierName.isPresent() ? supplierName.get() : null;
    }

    private List<AttachmentInfo> getAttachmentInfoForParameter(final Parameter parameter) {

        return parameter.getValues().getValue().stream()
                .map(value -> AttachmentInfo.builder().parameterId(parameter.getParameterId())
                        .attachmentId(value.getId()).attachmentName(value.getValue())
                        .secureToken(value.getSecureToken()).build())
                .collect(Collectors.toList());
    }


    private ExportRfxResponse getSingleRfx(final String externalEventId) {
        return jaggaerService.searchRFx(Set.of(externalEventId)).stream().findFirst().orElseThrow(
                () -> new TendersDBDataException(format(ERR_MSG_RFX_NOT_FOUND, externalEventId)));
    }

    private ExportRfxResponse getRfxWithSuppliers(final String externalEventId) {
        ExportRfxResponse exportRfxResponse=jaggaerService.getRfxWithSuppliers(externalEventId);

        if(Objects.isNull(exportRfxResponse)){
            throw new TendersDBDataException(format(ERR_MSG_RFX_NOT_FOUND, externalEventId));
        }
        return exportRfxResponse;
    }




    /**
     * Sign Contract
     *
     * @param procId
     * @param eventId
     * @param principal
     */
    @Transactional
    public void signProcurement(final Integer procId, final String eventId, final Contract request,
                                final String principal) {
        var user = userProfileService.resolveBuyerUserProfile(principal)
                .orElseThrow(() -> new AuthorisationFailureException(ERR_MSG_JAGGAER_USER_NOT_FOUND));
        var event = validationService.validateProjectAndEventIds(procId, eventId);
        awardService.getAwardOrPreAwardDetails(procId, eventId, AwardState.AWARD);
        event.setTenderStatus(COMPLETE_STATUS);
        event.setUpdatedBy(principal);
        event.setUpdatedAt(Instant.now());
        retryableTendersDBDelegate.save(event);
        var contractDetails =
                ContractDetails.builder().awardId(request.getAwardID()).contractStatus(request.getStatus())
                        .createdBy(user.getEmail()).createdAt(Instant.now()).event(event).build();
        retryableTendersDBDelegate.save(contractDetails);
    }

    /**
     * get Contract
     *
     * @param procId
     * @param eventId
     * @param principal
     */
    public Contract getContract(final Integer procId, final String eventId, final String principal) {
        userProfileService.resolveBuyerUserProfile(principal)
                .orElseThrow(() -> new AuthorisationFailureException(ERR_MSG_JAGGAER_USER_NOT_FOUND));
        var event = validationService.validateProjectAndEventIds(procId, eventId);
        var awardDetails = retryableTendersDBDelegate.findByEventId(event.getId())
                .orElseThrow(() -> new ResourceNotFoundException(CONTRACT_DETAILS_NOT_FOUND));
        return new Contract().id(awardDetails.getContractId()).awardID(awardDetails.getAwardId())
                .dateSigned(awardDetails.getCreatedAt())
                .status(awardDetails.getContractStatus());
    }


    private void retrieveAndUploadDocuments(String principal, ProcurementEvent procurementEvent) {
        List<RetrieveDocumentCallable> retrieveDocumentCallableList = procurementEvent.getDocumentUploads().stream().filter(du -> VirusCheckStatus.SAFE == du.getExternalStatus()).map(documentUpload -> new RetrieveDocumentCallable(documentUploadService, documentUpload, principal)).toList();
        List<Future<Map<DocumentUpload, ByteArrayMultipartFile>>> retriveDocumentfutures = null;
        try {
            retriveDocumentfutures = executorService.invokeAll(retrieveDocumentCallableList);
        } catch (InterruptedException e) {
            // TODO : Handle this and propagate your response properly
            throw new RuntimeException(e);
        }


        List<DocumentUploadCallable> documentUploadCallableList = new ArrayList<>();

        retriveDocumentfutures.forEach(retriveDocumentfuture -> {

            Map<DocumentUpload, ByteArrayMultipartFile> documentMap = null;

            try {
                documentMap = retriveDocumentfuture.get();
            } catch (InterruptedException | ExecutionException exception) {
                // TODO : Handle this and propagate your response properly
                throw new RuntimeException(exception);
            }

            var documentUpload = documentMap.keySet().stream().findFirst().get();
            documentUploadCallableList.add(new DocumentUploadCallable(jaggaerService, procurementEvent, documentUpload, documentMap.get(documentUpload)));
        });

        try {

            jaggerUploadExecutorService.invokeAll(documentUploadCallableList);

        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
    
    public void startEvaluation(final String profile, final Integer procId, final String eventId) {
      var procurementEvent = validationService.validateProjectAndEventIds(procId, eventId);
      var buyerUser = userProfileService.resolveBuyerUserProfile(profile)
          .orElseThrow(() -> new AuthorisationFailureException(JAGGAER_USER_NOT_FOUND));
      jaggaerService.startEvaluation(procurementEvent, buyerUser.getUserId());
    }
    
    /**
    *
    * EI-74 Add SalesForce endpoint to CAT service
    * 
    * Create Jaggaer Rfx from SalesForce request
    *
    * @param ProcurementProject
    * @param SalesforceProjectTender
    * @param principal
    * @return CreateUpdateRfx
    */
    public CreateUpdateRfx createSalesforceRfxRequest(final ProcurementProject project, final SalesforceProjectTender projectTender,
                                             final String principal) {

      // Fetch Jaggaer ID and Buyer company ID from Jaggaer config     
  	  var ownerUser = OwnerUser.builder().login(projectTender.getRfx().getOwnerUserLogin()).build();
      var jaggaerBuyerCompanyId =  jaggaerAPIConfig.getAssistedProcurementId();
      var buyerCompany = BuyerCompany.builder().id(jaggaerBuyerCompanyId).build();
      
      var additionalInfoProcurementRoute = AdditionalInfo.builder()
      		.name(ADDITIONAL_INFO_PROCUREMENT_ROUTE)
      		.type(ADDITIONAL_INFO_PROCUREMENT_ROUTE_TYPE)
      		.visibleToSupplier(0)
              .label(ADDITIONAL_INFO_PROCUREMENT_ROUTE)
              .labelLocale(ADDITIONAL_INFO_LOCALE)
              .values(
                      new AdditionalInfoValues(Arrays.asList(new AdditionalInfoValue(projectTender.getRfx().getProcurementRoute()))))
              .build();


      var additionalInfoFramework = AdditionalInfo.builder().name(ADDITIONAL_INFO_FRAMEWORK_NAME)
              .label(ADDITIONAL_INFO_FRAMEWORK_NAME).labelLocale(ADDITIONAL_INFO_LOCALE)
              .values(
                      new AdditionalInfoValues(Arrays.asList(new AdditionalInfoValue(projectTender.getRfx().getFrameworkRMNumber()))))
              .build();

      var additionalInfoLot =
              AdditionalInfo.builder().name(ADDITIONAL_INFO_LOT_NUMBER).label(ADDITIONAL_INFO_LOT_NUMBER)
                      .labelLocale(ADDITIONAL_INFO_LOCALE).values(new AdditionalInfoValues(
                              Arrays.asList(new AdditionalInfoValue(projectTender.getRfx().getFrameworkLotNumber()))))
                      .build();

      var rfxAdditionalInfoList =
      			new RfxAdditionalInfoList(Arrays.asList(additionalInfoProcurementRoute, additionalInfoFramework, additionalInfoLot));

      String rfxType = "";
      Integer rfiFlag = 0;
      OperationCode operationCode=null;
      String rfxTemplateReferenceCode=null;
      
      if (projectTender.getRfx().getRfiFlag() != null && projectTender.getRfx().getRfiFlag().equals("1"))
      {
		rfxType = "STANDARD_PQQ";
		rfiFlag = 1;
		rfxTemplateReferenceCode=null;
		operationCode = OperationCode.CREATE;
		
      } else {

    	log.debug("call findRfxTemplateMappingByCommercialAgreementNumberAndLotNumber() with arg {}, {}", 
				  	projectTender.getRfx().getFrameworkRMNumber(), projectTender.getRfx().getFrameworkLotNumber());
		Optional<RfxTemplateMapping> rfxTemplateMapping = 
		   		retryableTendersDBDelegate.findRfxTemplateMappingByCommercialAgreementNumberAndLotNumber(
		   								projectTender.getRfx().getFrameworkRMNumber(),
		   								projectTender.getRfx().getFrameworkLotNumber());
		log.debug("rfxTemplateMapping {}", rfxTemplateMapping);
		
		rfxTemplateReferenceCode = rfxTemplateMapping.map(RfxTemplateMapping::getRfxReferenceCode).get();
		log.debug("rfxTemplateReferenceCode {}", rfxTemplateReferenceCode);

        rfxType = "STANDARD_ITT";
      	operationCode = OperationCode.CREATE_FROM_TEMPLATE;
      }
      
      RfxSetting rfxSetting =
          RfxSetting.builder()
				.shortDescription(projectTender.getRfx().getShortDescription())
				.longDescription(projectTender.getRfx().getShortDescription())
          		.rfiFlag(rfiFlag)
          		.value(Integer.valueOf(projectTender.getRfx().getValue()))
  				.templateReferenceCode(rfxTemplateReferenceCode)
  				.tenderReferenceCode(project.getExternalReferenceId())
  				.rfxReferenceCode(project.getExternalReferenceId())
                .buyerCompany(buyerCompany)
                .ownerUser(ownerUser)
                .rfxType(rfxType)
          		.qualEnvStatus(Integer.valueOf(projectTender.getRfx().getQualEnvStatus()))                    
          		.techEnvStatus(Integer.valueOf(projectTender.getRfx().getTechEnvStatus()))                    
          		.commEnvStatus(Integer.valueOf(projectTender.getRfx().getCommEnvStatus()))
          		.visibilityEGComments(Integer.valueOf(projectTender.getRfx().getVisibilityEGComments()))
          		.rankingStrategy(projectTender.getRfx().getRankingStrategy())
          		.publishDate(OffsetDateTime.parse(projectTender.getRfx().getPublishDate()))
          		.closeDate(OffsetDateTime.parse(projectTender.getRfx().getCloseDate()))
          		.build();  

      log.debug("rfxSetting {}", rfxSetting);
      log.debug("rfxAdditionalInfoList {}", rfxAdditionalInfoList);


      var rfx = Rfx.builder()
      		.rfxSetting(rfxSetting)
              .rfxAdditionalInfoList(rfxAdditionalInfoList)
              .build();
            
      return new CreateUpdateRfx(operationCode, rfx);
    }
    
    /**
    *
    * EI-97 Add deltas endpoint to CAT service
    * 
    * Get Jaggaer project deltas from last update request
    *
    * @paramProcurementProject
    * @paramSalesforceProjectTender
    * @paramprincipal
    * @returnCreateUpdateRfx
    */
    public List<Release> getProjectUpdatesByLastUpdateDate(@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) final Date lastSuccessRun, 
    									final String buyerCompanyId) {

    	List<Release> updateList = jaggaerService.getRfxByLastUpdateDate(lastSuccessRun,buyerCompanyId).stream()
    									.filter(e -> filterExportRfxResponse(e))
    									.map(e -> {
    										// Release
    										Release r = new Release();
    									    
    										Optional<ProcurementEvent> procurementEvent = retryableTendersDBDelegate
    												.findProcurementEventByExternalEventIdAndExternalReferenceId(
    															e.getRfxSetting().getRfxId(), 
    															e.getRfxSetting().getRfxReferenceCode());

    										r.setOcid(procurementEvent.get().getEventID());    											
    										r.setId(procurementEvent.get().getProject().getId().toString());
    										r.setDate(e.getRfxSetting().getPublishDate());
    										
    										OcdsTenderStatus ocdsStateMapping =getOcdsStateMapping(e.getRfxSetting().getStatusCode().toString(),
    																					e.getRfxSetting().getStatus(),
    																					e.getRfxSetting().getCloseDate());
    										r.setTag(ocdsStateMapping.getReleaseTag());
    										r.setInitiationType(InitiationType.TENDER);
    										
    										// Tender1
    										Tender1 t = new Tender1();
    										t.setDescription(e.getRfxSetting().getShortDescription());
    										t.setId(e.getRfxSetting().getRfxId());
    										t.setStatus(ocdsStateMapping.getTenderStatus());
    										Period1 tp = new Period1();
    										tp.setEndDate(e.getRfxSetting().getCloseDate());
    										t.setTenderPeriod(tp);
    										r.setTender(t);
    										
    										// Planning / Milestone / Status & Type
    										Planning1 p = new Planning1();
    										Milestone1 m = new Milestone1();
    										m.setStatus(ocdsStateMapping.getMilestoneStatus());
    										m.setType(ocdsStateMapping.getMilestoneType());
    										List<Milestone1> l = new ArrayList<Milestone1>();
    										l.add(m);
    										p.setMilestones(l);
    										r.setPlanning(p);
    										
    										return r;    										
    									})
    									.collect(Collectors.toList());
    	
    	return updateList;

    }
    
    /**
    *
    * EI-97 Add deltas endpoint to CAT service
    * 
    * Get tender status OCDS mapping values
    * 
    * see doc "ES-States-MappingJaggaertoOCDStoSaleforce-220323-1058.pdf"
    * 
    * This filter ensures only events found in CAS DB (for the current environment i.e. local/sbx3/etc.) are returned.
    * 
    * @paramexportRfxResponse
    * @returnBoolean
    */
    private Boolean filterExportRfxResponse (final ExportRfxResponse exportRfxResponse) {
    	
		Optional<ProcurementEvent> procurementEvent = retryableTendersDBDelegate
				.findProcurementEventByExternalEventIdAndExternalReferenceId(
						exportRfxResponse.getRfxSetting().getRfxId(), 
						exportRfxResponse.getRfxSetting().getRfxReferenceCode());

		log.debug("procurementEvent {}", procurementEvent);
		if (!procurementEvent.isEmpty()) {
			return true;
		} 
    	return false;
    }
    
    /**
    *
    * EI-97 Add deltas endpoint to CAT service
    * 
    * Get tender status OCDS mapping values
    * 
    * see doc "ES-States-MappingJaggaertoOCDStoSaleforce-220323-1058.pdf"
    *
    * @paramstatusCode
    * @paramstatus
    * @paramtenderPeriodEndDate
    * @returnOcdsTenderStatus
    */
    private OcdsTenderStatus getOcdsStateMapping(final String statusCode, final String status, final OffsetDateTime tenderPeriodEndDate) {
    	
    	OffsetDateTime nowDateTime = OffsetDateTime.now();
    	
    	if (( status.equalsIgnoreCase(JAGGAER_STATUS_TO_BE_PUBLISHED) &&
    			statusCode.equalsIgnoreCase(JAGGAER_STATUS_CODE_TO_BE_PUBLISHED))) {
    		return OcdsTenderStatus.builder()
    				.tenderStatus(TenderStatus.PLANNED)
    				.releaseTag(ReleaseTag.PLANNING)
    				.milestoneStatus(MilestoneStatus.SCHEDULED)
    				.milestoneType(MilestoneType.PREPROCUREMENT)
    				.build();    				
    	}

    	// Now < "tenderPeriod": "endDate"    	
		if (nowDateTime.isBefore(tenderPeriodEndDate) && 
				status.equalsIgnoreCase(JAGGAER_STATUS_RUNNING) &&
    			statusCode.equalsIgnoreCase(JAGGAER_STATUS_CODE_RUNNING)) {
    		return OcdsTenderStatus.builder()
    				.tenderStatus(TenderStatus.ACTIVE)
    				.releaseTag(ReleaseTag.TENDER)
    				.build();    				
		}
    	

    	// Now >= "tenderPeriod": "endDate"
		if ( (nowDateTime.isEqual(tenderPeriodEndDate) || nowDateTime.isAfter(tenderPeriodEndDate)) &&
				status.equalsIgnoreCase(JAGGAER_STATUS_TO_BE_EVALUATED) &&
    			statusCode.equalsIgnoreCase(JAGGAER_STATUS_CODE_TO_BE_EVALUATED)) {
    		return OcdsTenderStatus.builder()
    				.tenderStatus(TenderStatus.ACTIVE)
    				.releaseTag(ReleaseTag.TENDER)
    				.build();    				
    	}

    	// Now >= "tenderPeriod": "endDate"
		if ( (nowDateTime.isEqual(tenderPeriodEndDate) || nowDateTime.isAfter(tenderPeriodEndDate)) &&
				status.equalsIgnoreCase(JAGGAER_STATUS_TECHNICAL_EVALUATION) &&
    			statusCode.equalsIgnoreCase(JAGGAER_STATUS_CODE_TECHNICAL_EVALUATION)) {
    		return OcdsTenderStatus.builder()
    				.tenderStatus(TenderStatus.ACTIVE)
    				.releaseTag(ReleaseTag.TENDER)
    				.build();    				
    	}

    	// Now >= "tenderPeriod": "endDate"
		if ( (nowDateTime.isEqual(tenderPeriodEndDate) || nowDateTime.isAfter(tenderPeriodEndDate)) &&
				status.equalsIgnoreCase(JAGGAER_STATUS_COMMERCIAL_EVALUATION) &&
    			statusCode.equalsIgnoreCase(JAGGAER_STATUS_CODE_COMMERCIAL_EVALUATION)) {
    		return OcdsTenderStatus.builder()
    				.tenderStatus(TenderStatus.ACTIVE)
    				.releaseTag(ReleaseTag.TENDER)
    				.build();    				
    	}

		if (( status.equalsIgnoreCase(JAGGAER_STATUS_FINAL_EVALUATION) &&
    			statusCode.equalsIgnoreCase(JAGGAER_STATUS_CODE_FINAL_EVALUATION))) {
    		return OcdsTenderStatus.builder()
    				.tenderStatus(TenderStatus.COMPLETE)
    				.releaseTag(ReleaseTag.TENDER)
    				.build();    				
    	}

		if (( status.equalsIgnoreCase(JAGGAER_STATUS_CLOSED) &&
    			statusCode.equalsIgnoreCase(JAGGAER_STATUS_CODE_CLOSED))) {
    		return OcdsTenderStatus.builder()
    				.tenderStatus(TenderStatus.UNSUCCESSFUL)
    				.build();    				
    	}

		if (( status.equalsIgnoreCase(JAGGAER_STATUS_FINAL_EVALUATION_PRE_AWARDED) &&
    			statusCode.equalsIgnoreCase(JAGGAER_STATUS_CODE_FINAL_EVALUATION_PRE_AWARDED))) {
    		return OcdsTenderStatus.builder()
    				.awardStatus(AwardStatus.PENDING)
    				.releaseTag(ReleaseTag.AWARD)
    				.build();    				
    	}

		if (( status.equalsIgnoreCase(JAGGAER_STATUS_AWARDED) &&
    			statusCode.equalsIgnoreCase(JAGGAER_STATUS_CODE_AWARDED))) {
    		return OcdsTenderStatus.builder()
    				.awardStatus(AwardStatus.ACTIVE)
    				.releaseTag(ReleaseTag.AWARD)
    				.build();    				
    	}

		if (( status.equalsIgnoreCase(JAGGAER_STATUS_AWARDED_TO_OFFLINE_RESPONSE) &&
    			statusCode.equalsIgnoreCase(JAGGAER_STATUS_CODE_AWARDED_TO_OFFLINE_RESPONSE))) {
    		return OcdsTenderStatus.builder()
    				.awardStatus(AwardStatus.ACTIVE)
    				.releaseTag(ReleaseTag.AWARD)
    				.build();    				
    	}

		if (( status.equalsIgnoreCase(JAGGAER_STATUS_NOT_AWARDED) &&
    			statusCode.equalsIgnoreCase(JAGGAER_STATUS_CODE_NOT_AWARDED))) {
    		return OcdsTenderStatus.builder()
    				.awardStatus(AwardStatus.CANCELLED)
    				.releaseTag(ReleaseTag.TENDERCANCELLATION)
    				.build();    				
    	}

		if (( status.equalsIgnoreCase(JAGGAER_STATUS_ROUND_CREATED_IN_NEW_RFQ) &&
    			statusCode.equalsIgnoreCase(JAGGAER_STATUS_CODE_ROUND_CREATED_IN_NEW_RFQ))) {
    		return OcdsTenderStatus.builder()
    				.awardStatus(AwardStatus.CANCELLED)
    				.releaseTag(ReleaseTag.IMPLEMENTATION)
    				.build();    				
    	}

		if (( status.equalsIgnoreCase(JAGGAER_STATUS_SUSPENDED) &&
    			statusCode.equalsIgnoreCase(JAGGAER_STATUS_CODE_SUSPENDED))) {
    		return OcdsTenderStatus.builder()
    				.tenderStatus(TenderStatus.WITHDRAWN)
    				.build();    				
    	}

		if (( status.equalsIgnoreCase(JAGGAER_STATUS_ENDED) &&
    			statusCode.equalsIgnoreCase(JAGGAER_STATUS_CODE_ENDED))) {
    		return OcdsTenderStatus.builder()
    				.tenderStatus(TenderStatus.CANCELLED)
    				.build();    				
    	}

		// no status determined
		return OcdsTenderStatus.builder().build();
    	
    }

}
