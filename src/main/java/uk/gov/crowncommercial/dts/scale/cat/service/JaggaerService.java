package uk.gov.crowncommercial.dts.scale.cat.service;

import static java.lang.String.format;
import static java.time.Duration.ofSeconds;
import static java.util.Optional.ofNullable;
import static org.springframework.http.HttpHeaders.ACCEPT;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig.ENDPOINT;

import java.net.URI;
import java.net.URISyntaxException;
import java.text.DateFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StopWatch;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import uk.gov.crowncommercial.dts.scale.cat.config.Constants;
import uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.exception.JaggaerApplicationException;
import uk.gov.crowncommercial.dts.scale.cat.exception.TendersDBDataException;
import uk.gov.crowncommercial.dts.scale.cat.model.DocumentAttachment;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.AwardState;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.DocumentAudienceType;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.PublishDates;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.*;
import static uk.gov.crowncommercial.dts.scale.cat.config.Constants.ERR_MSG_RFX_NOT_FOUND;

/**
 * Jaggaer Service.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JaggaerService {

  private final JaggaerAPIConfig jaggaerAPIConfig;
  private final WebClient jaggaerWebClient;
  private final WebclientWrapper webclientWrapper;
  private static final String MESSAGE_PARAMS =
      "MESSAGE_BODY;MESSAGE_CATEGORY;MESSAGE_ATTACHMENT;MESSAGE_READING";

  private static final String ERRCODE_SUBUSER_EXISTS = "112(loginSubUser)";
  private static final String ERRCODE_SUPERUSER_EXISTS = "112(USER_ALIAS)";
  
  private static final String JAGGAER_API_DATEFORMATTER = "yyyy-MM-dd HH:mm:ss.SSS Z";

  /**
   * Create or update a Project.
   *
   * @param createUpdateProject
   * @return
   */
  public CreateUpdateProjectResponse createUpdateProject(
      final CreateUpdateProject createUpdateProject) {

    final var updateProjectResponse =
        ofNullable(jaggaerWebClient.post().uri(jaggaerAPIConfig.getCreateProject().get(ENDPOINT))
            .bodyValue(createUpdateProject).retrieve().bodyToMono(CreateUpdateProjectResponse.class)
            .block(Duration.ofSeconds(jaggaerAPIConfig.getTimeoutDuration())))
                .orElseThrow(() -> new JaggaerApplicationException(INTERNAL_SERVER_ERROR.value(),
                    "Unexpected error updating project"));

    if (updateProjectResponse.getReturnCode() != 0
        || !"OK".equals(updateProjectResponse.getReturnMessage())) {
      throw new JaggaerApplicationException(updateProjectResponse.getReturnCode(),
          updateProjectResponse.getReturnMessage());
    }
    log.info("Updated project: {}", updateProjectResponse);

    return updateProjectResponse;
  }

  /**
   * Create or update an Rfx (Event).
   *
   * @param rfx
   * @return
   */
  public CreateUpdateRfxResponse createUpdateRfx(final Rfx rfx, final OperationCode operationCode) {

    final var createRfxResponse =
        ofNullable(jaggaerWebClient.post().uri(jaggaerAPIConfig.getCreateRfx().get(ENDPOINT))
            .bodyValue(new CreateUpdateRfx(operationCode, rfx)).retrieve()
            .bodyToMono(CreateUpdateRfxResponse.class)
            .block(ofSeconds(jaggaerAPIConfig.getTimeoutDuration())))
                .orElseThrow(() -> new JaggaerApplicationException(INTERNAL_SERVER_ERROR.value(),
                    "Unexpected error updating Rfx"));

    if (createRfxResponse.getReturnCode() != 0
        || !Constants.OK_MSG.equals(createRfxResponse.getReturnMessage())) {
      log.error(createRfxResponse.toString());
      throw new JaggaerApplicationException(createRfxResponse.getReturnCode(),
          createRfxResponse.getReturnMessage());
    }
    log.info("Updated event: {}", createRfxResponse);
    return createRfxResponse;
  }

  /**
   * Get an Rfx (Event).
   *
   * @deprecated This method (or rather endpoint that it calls) is non-performant and should be
   *             replaced with calls through {@link #searchRFx(Set)} instead where possible
   * @param externalEventId
   * @return
   */
  @Deprecated
  public ExportRfxResponse getRfx(final String externalEventId) {

    final var exportRfxUri = jaggaerAPIConfig.getExportRfx().get(ENDPOINT);
    return ofNullable(jaggaerWebClient.get().uri(exportRfxUri, externalEventId).retrieve()
        .bodyToMono(ExportRfxResponse.class)
        .block(ofSeconds(jaggaerAPIConfig.getTimeoutDuration())))
            .orElseThrow(() -> new JaggaerApplicationException(INTERNAL_SERVER_ERROR.value(),
                "Unexpected error retrieving rfx"));
  }


  public ExportRfxResponse getRfxWithEmailRecipients(final String externalEventId) {
    //TODO: This can be a candidate for cache
    final var exportRfxUri = jaggaerAPIConfig.getExportRfxWithEmailRecipients().get(ENDPOINT);
    return ofNullable(jaggaerWebClient.get().uri(exportRfxUri, externalEventId).retrieve()
            .bodyToMono(ExportRfxResponse.class)
            .block(ofSeconds(jaggaerAPIConfig.getTimeoutDuration())))
            .orElseThrow(() -> new JaggaerApplicationException(INTERNAL_SERVER_ERROR.value(),
                    "Unexpected error retrieving rfx"));
  }

  public ExportRfxResponse getRfxWithSuppliers(final String externalEventId) {

    final var exportRfxUri = jaggaerAPIConfig.getExportRfxWithSuppliers().get(ENDPOINT);
    return ofNullable(jaggaerWebClient.get().uri(exportRfxUri, externalEventId).retrieve()
            .bodyToMono(ExportRfxResponse.class)
            .block(ofSeconds(jaggaerAPIConfig.getTimeoutDuration())))
            .orElseThrow(() -> new JaggaerApplicationException(INTERNAL_SERVER_ERROR.value(),
                    "Unexpected error retrieving rfx"));
  }

  public ExportRfxResponse getRfxWithSuppliersOffersAndResponseCounters(final String externalEventId) {

    final var exportRfxUri = jaggaerAPIConfig.getExportRfxWithSuppliersOffersAndResponseCounters().get(ENDPOINT);
    return ofNullable(jaggaerWebClient.get().uri(exportRfxUri, externalEventId).retrieve()
            .bodyToMono(ExportRfxResponse.class)
            .block(ofSeconds(jaggaerAPIConfig.getTimeoutDuration())))
            .orElseThrow(() -> new JaggaerApplicationException(INTERNAL_SERVER_ERROR.value(),
                    "Unexpected error retrieving rfx"));
  }

  public ExportRfxResponse getRfxWithWithBuyerAndSellerAttachments(final String externalEventId) {

    final var exportRfxUri = jaggaerAPIConfig.getExportRfxWithBuyerAndSellerAttachments().get(ENDPOINT);
    return ofNullable(jaggaerWebClient.get().uri(exportRfxUri, externalEventId).retrieve()
            .bodyToMono(ExportRfxResponse.class)
            .block(ofSeconds(jaggaerAPIConfig.getTimeoutDuration())))
            .orElseThrow(() -> new JaggaerApplicationException(INTERNAL_SERVER_ERROR.value(),
                    "Unexpected error retrieving rfx"));
  }


  /**
   * Searches for an Rfx by <code>rfxId</code> filter. Without any components this simply returns
   * the top-level <code>rfxSetting</code> data and is far more performant that getting the entire
   * Rfx with all components.
   *
   * @param externalEventIds
   * @return the rfx, if a single record found in response data list
   */
  public Set<ExportRfxResponse> searchRFx(final Set<String> externalEventIds) {

    var searchRfxUri = jaggaerAPIConfig.getSearchRfxSummary().get(ENDPOINT);
    var rfxIds = externalEventIds.stream().collect(Collectors.joining(","));

    var searchRfxResponse = webclientWrapper
        .getOptionalResource(SearchRfxsResponse.class, jaggaerWebClient,
            jaggaerAPIConfig.getTimeoutDuration(), searchRfxUri, rfxIds)
        .orElseThrow(() -> new JaggaerApplicationException(INTERNAL_SERVER_ERROR.value(),
            "Unexpected error searching rfxs"));

    if (searchRfxResponse.getReturnCode() == 0) {
      return searchRfxResponse.getDataList().getRfx();
    }
    throw new JaggaerApplicationException(INTERNAL_SERVER_ERROR.value(),
        "Unexpected error searching rfxs");
  }
  
  /**
   * Get an Rfx by component(Event).
   *
   * @param externalEventId
   * @param components
   * @return
   */
  public ExportRfxResponse getRfxByComponent(final String externalEventId, final Set<String> components) {

    final var rfxUri = jaggaerAPIConfig.getGetRfxByComponent().get(ENDPOINT);
    var componentFilters = components.stream().collect(Collectors.joining(";"));
    
    return ofNullable(jaggaerWebClient.get().uri(rfxUri, externalEventId, componentFilters).retrieve()
        .bodyToMono(ExportRfxResponse.class)
        .block(ofSeconds(jaggaerAPIConfig.getTimeoutDuration())))
            .orElseThrow(() -> new JaggaerApplicationException(INTERNAL_SERVER_ERROR.value(),
                "Unexpected error retrieving rfx"));
  }


  /**
   * Create or update a company and/or sub-users
   *
   * @param createUpdateCompanyRequest
   * @return response containing code, message and bravoId of company
   */
  public CreateUpdateCompanyResponse createUpdateCompany(
      final CreateUpdateCompanyRequest createUpdateCompanyRequest) {
    final var createUpdateCompanyResponse = webclientWrapper.postData(createUpdateCompanyRequest,
        CreateUpdateCompanyResponse.class, jaggaerWebClient, jaggaerAPIConfig.getTimeoutDuration(),
        jaggaerAPIConfig.getCreateUpdateCompany().get(ENDPOINT));

    log.debug("Create update company response: {}", createUpdateCompanyResponse);

    // Super user exists is code "-996"...
    var jaggaerSuccessCodes = Set.of("0", "1", "-996");

    if (!jaggaerSuccessCodes.contains(createUpdateCompanyResponse.getReturnCode())) {
      throw new JaggaerApplicationException(createUpdateCompanyResponse.getReturnCode(),
          createUpdateCompanyResponse.getReturnMessage());
    }

    if (createUpdateCompanyResponse.getReturnMessage().contains(ERRCODE_SUBUSER_EXISTS)
        || createUpdateCompanyResponse.getReturnMessage().contains(ERRCODE_SUPERUSER_EXISTS)) {
      throw new JaggaerApplicationException(createUpdateCompanyResponse.getReturnCode(),
          "Jaggaer sub or super user already exists: "
              + createUpdateCompanyResponse.getReturnMessage());
    }

    if ("1".equals(createUpdateCompanyResponse.getReturnCode())) {
      log.warn("Create / update company operation succeeded with warnings: [{}]",
          createUpdateCompanyResponse.getReturnMessage());
    }
    return createUpdateCompanyResponse;

  }

  /**
   * Upload a document at the Rfx level.
   *
   * @param multipartFile
   * @param rfx
   */
  public void uploadDocument(final MultipartFile multipartFile, final CreateUpdateRfx rfx) {

    log.info("uploadDocument");

    if (multipartFile.getOriginalFilename() == null) {
      throw new IllegalArgumentException("No filename specified for upload document attachment");
    }

    final MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
    parts.add("data", rfx);
    parts.add(multipartFile.getOriginalFilename(), multipartFile.getResource());

    final var response =
        ofNullable(jaggaerWebClient.post().uri(jaggaerAPIConfig.getCreateRfx().get(ENDPOINT))
            .contentType(MediaType.MULTIPART_FORM_DATA).body(BodyInserters.fromMultipartData(parts))
            .retrieve().bodyToMono(CreateUpdateRfxResponse.class).block())
                .orElseThrow(() -> new JaggaerApplicationException(
                    "Upload attachment from Jaggaer returned a null response: rfxId:"
                        + rfx.getRfx().getRfxSetting().getRfxId()));

    if (0 != response.getReturnCode()) {
      throw new JaggaerApplicationException(response.getReturnCode(), response.getReturnMessage());
    }
  }

  /**
   * Retrieve a document attachment.
   *
   * @param fileId
   * @param fileName
   * @return
   */
  public DocumentAttachment getDocument(final Integer fileId, final String fileName) {

    log.info("getDocument: {}, {}", fileId, fileName);

    final var getAttachmentUri = jaggaerAPIConfig.getGetAttachment().get(ENDPOINT);
    final var response = jaggaerWebClient.get().uri(getAttachmentUri, fileId, fileName)
        .header(ACCEPT, MediaType.APPLICATION_OCTET_STREAM_VALUE).retrieve().toEntity(byte[].class)
        .block();

    if (response == null) {
      throw new JaggaerApplicationException(
          "Get attachment from Jaggaer returned a null response: fileId:" + fileId + ", fileName: "
              + fileName);
    }
    return DocumentAttachment.builder().data(response.getBody())
        .contentType(response.getHeaders().getContentType()).build();
  }

  /**
   * publish Rfx
   *
   * @param event
   * @param publishDates
   * @param jaggaerUserId
   */
  public void publishRfx(final ProcurementEvent event, final PublishDates publishDates,
      final String jaggaerUserId) {

    // TODO: What do we do with `publishDate.startDate`, if supplied?

    final var publishRfx = PublishRfx.builder().rfxId(event.getExternalEventId())
        .rfxReferenceCode(event.getExternalReferenceId())
        .operatorUser(OwnerUser.builder().id(jaggaerUserId).build())
        .newClosingDate(publishDates.getEndDate().toInstant()).build();

    final var publishRfxEndpoint = jaggaerAPIConfig.getPublishRfx().get(ENDPOINT);

    final var publishRfxResponse = webclientWrapper.postData(publishRfx, PublishRfxResponse.class,
        jaggaerWebClient, jaggaerAPIConfig.getTimeoutDuration(), publishRfxEndpoint);

    log.debug("Publish event response: {}", publishRfxResponse);

    if (!Objects.equals(0, publishRfxResponse.getReturnCode())
        || !Constants.OK_MSG.equals(publishRfxResponse.getReturnMessage())) {
      throw new JaggaerApplicationException(publishRfxResponse.getReturnCode(),
          publishRfxResponse.getReturnMessage());
    }
  }

  /**
   * Get projects list from Jaggaer
   *
   * @return ProjectList
   * @param jaggaerUserId
   */
  public ProjectListResponse getProjectList(final String jaggaerUserId) {
    final var projectListUri = jaggaerAPIConfig.getGetProjectList().get(ENDPOINT);
    final var filters = "projectOwnerId==" + jaggaerUserId;

    return ofNullable(jaggaerWebClient.get().uri(projectListUri, filters).retrieve()
        .bodyToMono(ProjectListResponse.class)
        .block(ofSeconds(jaggaerAPIConfig.getTimeoutDuration())))
            .orElseThrow(() -> new JaggaerApplicationException(INTERNAL_SERVER_ERROR.value(),
                "Unexpected error retrieving projects"));
  }

  public MessagesResponse getMessages(final String externalEventId, final Integer pageSize) {
    final var messagesUrl = jaggaerAPIConfig.getGetMessages().get(ENDPOINT);
    final var start = pageSize > 1 ? pageSize + 1 : 1;
    final var filters = "objectReferenceCode==" + externalEventId;

    return ofNullable(jaggaerWebClient.get().uri(messagesUrl, filters, MESSAGE_PARAMS, start)
        .retrieve().bodyToMono(MessagesResponse.class)
        .block(ofSeconds(jaggaerAPIConfig.getTimeoutDuration())))
            .orElseThrow(() -> new JaggaerApplicationException(INTERNAL_SERVER_ERROR.value(),
                "Unexpected error retrieving messages"));
  }

  public Message getMessage(final String messageId) {
    final var messagesUrl = jaggaerAPIConfig.getGetMessage().get(ENDPOINT);

    return ofNullable(jaggaerWebClient.get().uri(messagesUrl, messageId, MESSAGE_PARAMS).retrieve()
        .bodyToMono(uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.Message.class)
        .block(ofSeconds(jaggaerAPIConfig.getTimeoutDuration())))
            .orElseThrow(() -> new JaggaerApplicationException(INTERNAL_SERVER_ERROR.value(),
                "Unexpected error retrieving messages"));
  }
 @Async
  public MessageResponse updateMessage(final MessageUpdate messageUpdate) {
    final var updateMessageUrl = jaggaerAPIConfig.getUpdateMessage().get(ENDPOINT);

    return ofNullable(jaggaerWebClient.put().uri(updateMessageUrl).body(Mono.just(messageUpdate), MessageUpdate.class).retrieve()
            .bodyToMono(MessageResponse.class)
            .block(ofSeconds(jaggaerAPIConfig.getTimeoutDuration())))
            .orElseThrow(() -> new JaggaerApplicationException(INTERNAL_SERVER_ERROR.value(),
                    "Unexpected error retrieving messages"));
  }
  /**
   * Start Evaluation Rfx
   *
   * @param event
   * @param jaggaerUserId
   */
  public void startEvaluation(final ProcurementEvent event, final String jaggaerUserId) {
    final var startEvaluationRequest = RfxWorkflowRequest.builder()
        .rfxId(event.getExternalEventId()).rfxReferenceCode(event.getExternalReferenceId())
        .operatorUser(OwnerUser.builder().id(jaggaerUserId).build()).build();

    final var endPoint = jaggaerAPIConfig.getStartEvaluation().get(ENDPOINT);
    final var evaluationResponse =
        webclientWrapper.postData(startEvaluationRequest, WorkflowRfxResponse.class,
            jaggaerWebClient, jaggaerAPIConfig.getTimeoutDuration(), endPoint);

    log.debug("Start evaluation event response: {}", evaluationResponse);
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

    Instant retrieveDocStart= Instant.now();

    this.uploadDocument(multipartFile, update);
    Instant retrieveDocEnd= Instant.now();

      log.info("JaggaerService : eventUploadDocument  : Total time taken to uploadDocument service for procID {} : eventId :{} , Filename : {},  Timetaken : {}  ",event.getProject().getId(), event.getEventID(),fileName,
              Duration.between(retrieveDocStart,retrieveDocEnd).toMillis());


  }

  /**
   * extend Rfx
   *
   * @param rfx
   * @param operationCode
   *
   */
  public CreateUpdateRfxResponse extendRfx(final RfxRequest rfx,
      final OperationCode operationCode) {

    final var extendRfxResponse = webclientWrapper.postData(new ExtendEventRfx(operationCode, rfx),
        CreateUpdateRfxResponse.class, jaggaerWebClient, jaggaerAPIConfig.getTimeoutDuration(),
        jaggaerAPIConfig.getCreateRfx().get(ENDPOINT));

    if (extendRfxResponse.getReturnCode() != 0
        || !Constants.OK_MSG.equals(extendRfxResponse.getReturnMessage())) {
      log.error(extendRfxResponse.toString());
      throw new JaggaerApplicationException(extendRfxResponse.getReturnCode(),
          extendRfxResponse.getReturnMessage());
    }
    log.info("Extended event: {}", extendRfxResponse);
    return extendRfxResponse;
  }

  /**
   * Invalidate Event Rfx
   *
   * @param request
   */
  public void invalidateEvent(final InvalidateEventRequest request) {
    final var endPoint = jaggaerAPIConfig.getInvalidateEvent().get(ENDPOINT);
    final var response = webclientWrapper.postData(request, WorkflowRfxResponse.class,
        jaggaerWebClient, jaggaerAPIConfig.getTimeoutDuration(), endPoint);
    log.debug("Invalidate event response: {}", response);
  }


  /**
   * Invalidate Event Rfx
   *
   * @param request
 * @return 
   */
  public WorkflowRfxResponse invalidateSalesforceEvent(final InvalidateEventRequest request) {
    final var endPoint = jaggaerAPIConfig.getInvalidateEvent().get(ENDPOINT);
    final var response = webclientWrapper.postData(request, WorkflowRfxResponse.class,
        jaggaerWebClient, jaggaerAPIConfig.getTimeoutDuration(), endPoint);
    log.debug("Invalidate event response: {}", response);
    return response;
  }
  /**
   * Get Project
   *
   * @param externalProjectId
   */
  public Project getProject(final String externalProjectId) {
    return ofNullable(jaggaerWebClient.get()
        .uri(jaggaerAPIConfig.getGetProject().get(ENDPOINT), externalProjectId).retrieve()
        .bodyToMono(Project.class).block(Duration.ofSeconds(jaggaerAPIConfig.getTimeoutDuration())))
            .orElseThrow(() -> new JaggaerApplicationException(INTERNAL_SERVER_ERROR.value(),
                "Unexpected error retrieving project"));
  }
  
  /**
   * Award Rfx
   *
   * @param event
   * @param jaggaerUserId
   */
  public String awardOrPreAwardRfx(final ProcurementEvent event, final String jaggaerUserId,
      final String supplierId, AwardState awardState) {
    final var awardRequest = RfxWorkflowRequest.builder()
        .suppliersList(SuppliersList.builder()
            .supplier(Arrays.asList(Supplier.builder().id(supplierId).build())).build())
        .rfxReferenceCode(event.getExternalReferenceId())
        .operatorUser(OwnerUser.builder().id(jaggaerUserId).build()).build();
    
    var endPoint = jaggaerAPIConfig.getAward().get(ENDPOINT);
    if (awardState.equals(AwardState.PRE_AWARD)) {
      endPoint = jaggaerAPIConfig.getPreAward().get(ENDPOINT);
    }
    final var response = webclientWrapper.postData(awardRequest, WorkflowRfxResponse.class,
        jaggaerWebClient, jaggaerAPIConfig.getTimeoutDuration(), endPoint);
    log.debug("Award response: {}", response);
    
    if (!Objects.equals(0, response.getReturnCode())
        || !Constants.OK_MSG.equals(response.getReturnMessage())) {
      throw new JaggaerApplicationException(response.getReturnCode(),
          response.getReturnMessage());
    }
    return response.getFinalStatus();
  }
  
  /**
   * End Evaluation Rfx
   *
   * @param event
   * @param jaggaerUserId
   */
  public void completeTechnical(final ProcurementEvent event, final String jaggaerUserId) {
    final var completeTechnicalRequest = RfxWorkflowRequest.builder()
        .rfxId(event.getExternalEventId()).rfxReferenceCode(event.getExternalReferenceId())
        .operatorUser(OwnerUser.builder().id(jaggaerUserId).build()).build();

    final var endPoint = jaggaerAPIConfig.getCompleteTechnical().get(ENDPOINT);
    final var response =
        webclientWrapper.postData(completeTechnicalRequest, WorkflowRfxResponse.class,
            jaggaerWebClient, jaggaerAPIConfig.getTimeoutDuration(), endPoint);

    log.debug("Complete evaluation rfx response: {}", response);
  }

  /**
   * Open Envelope
   *
   * @param event
   * @param jaggaerUserId
   * @param envelopeType
   */
  public void openEnvelope(final ProcurementEvent event, final String jaggaerUserId,
      final EnvelopeType envelopeType) {
    final var openEnvelopeRequest = OpenEnvelopeWorkFlowRequest.builder().envelopeType(envelopeType)
        .rfxReferenceCode(event.getExternalReferenceId())
        .operatorUser(OwnerUser.builder().id(jaggaerUserId).build()).build();

    final var envelopeResponse = webclientWrapper.postData(openEnvelopeRequest,
        WorkflowRfxResponse.class, jaggaerWebClient, jaggaerAPIConfig.getTimeoutDuration(),
        jaggaerAPIConfig.getOpenEnvelope().get(ENDPOINT));

    log.debug("Open envelope response: {}", envelopeResponse);
    if (envelopeResponse.getReturnCode() != 0
        || !Constants.OK_MSG.equals(envelopeResponse.getReturnMessage())) {
      log.error(envelopeResponse.toString());
    }
  }

  /**
   * Generic method to call start evaluation and open envelope
   *
   * @param procurementEvent
   * @param jaggaerUserId
   */
  public void startEvaluationAndOpenEnvelope(final ProcurementEvent procurementEvent,
      final String jaggaerUserId) {
    startEvaluation(procurementEvent, jaggaerUserId);
    openEnvelope(procurementEvent, jaggaerUserId, EnvelopeType.TECH);
  }

  public ExportRfxResponse getSingleRfx(final String externalEventId) {
    return this.searchRFx(Set.of(externalEventId)).stream().findFirst().orElseThrow(
            () -> new TendersDBDataException(format(ERR_MSG_RFX_NOT_FOUND, externalEventId)));
  }
  
  /**
   * Create Reply Message Rfx
   *
   * @param messageRequest
   */
  public MessageResponse createReplyMessage(final CreateReplyMessage messageRequest) {
    final var endPoint = jaggaerAPIConfig.getCreateReplyMessage().get(ENDPOINT);
    final var messageResponse = webclientWrapper.postData(messageRequest, MessageResponse.class,
        jaggaerWebClient, jaggaerAPIConfig.getTimeoutDuration(), endPoint);
    log.debug("Create-Reply message response: {}", messageResponse);
    return messageResponse;
  }
  
  /**
   * Create Update Scores
   *
   * @param scoringRequest
   */
  public ScoringResponse createUpdateScores(
      ScoringRequest scoringRequest) {
    final var endPoint = jaggaerAPIConfig.getCreatUpdateScores().get(ENDPOINT);
    final var scoreResponse = webclientWrapper.postData(scoringRequest, ScoringResponse.class,
        jaggaerWebClient, jaggaerAPIConfig.getTimeoutDuration(), endPoint);
    log.debug("Create-update scoring response: {}", scoreResponse);
    if (scoreResponse.getReturnCode() != 0
        || !Constants.OK_MSG.equals(scoreResponse.getReturnMessage())) {
      log.error(scoreResponse.toString());
    }
    return scoreResponse;
  }
  
  /**
   * Get Rfxs by lastUpdateDate.
   *
   * @param lastUpdateDate
   * @param buyerCompanyID
   * @return
   */
  public Collection<ExportRfxResponse> getRfxByLastUpdateDate(@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) final Date lastUpdateDate, 
		  																final String buyerCompanyId) {

	var formattedLastUpdateDate = DateTimeFormatter.ofPattern(JAGGAER_API_DATEFORMATTER)
								.withZone(ZoneOffset.systemDefault()).format(lastUpdateDate.toInstant()).toString();
	log.info("getRfxByLastUpdateDate() - formattedLastUpdateDate {}", formattedLastUpdateDate);
			  
	final var rfxUri = jaggaerAPIConfig.getGetRfxByLastUpdateDateList().get(ENDPOINT);
    log.info("getRfxByLastUpdateDate() - rfxUri: {}", rfxUri);
   
    var updatedRfxs = webclientWrapper
            .getOptionalResource(SearchRfxsResponse.class, jaggaerWebClient,
                jaggaerAPIConfig.getTimeoutDuration(), rfxUri, formattedLastUpdateDate, buyerCompanyId,"")
            .orElseThrow(() -> new JaggaerApplicationException(INTERNAL_SERVER_ERROR.value(),
                "Unexpected error searching rfxs"));
    
    log.info("getRfxByLastUpdateDate() - updatedRfxs: {}", updatedRfxs);

    return updatedRfxs.getDataList().getRfx();
    
  }
  
}
