package uk.gov.crowncommercial.dts.scale.cat.service;

import static java.lang.String.format;
import static java.time.Duration.ofSeconds;
import static java.util.Optional.ofNullable;
import static org.springframework.http.HttpHeaders.ACCEPT;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig.ENDPOINT;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
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

  /**
   * Create or update a Project.
   *
   * @param createUpdateProject
   * @return
   */
  public CreateUpdateProjectResponse createUpdateProject(
          final CreateUpdateProject createUpdateProject) {

    log.info("Start calling Jaggaer API to Create or Update project. Request: {}", createUpdateProject);
    final var updateProjectResponse =
            ofNullable(jaggaerWebClient.post().uri(jaggaerAPIConfig.getCreateProject().get(ENDPOINT))
                    .bodyValue(createUpdateProject).retrieve().bodyToMono(CreateUpdateProjectResponse.class)
                    .block(Duration.ofSeconds(jaggaerAPIConfig.getTimeoutDuration())))
                    .orElseThrow(() -> new JaggaerApplicationException(INTERNAL_SERVER_ERROR.value(),
                            "Unexpected error updating project"));
    log.info("Finish calling Jaggaer API to Create or Update project. Response: {}", updateProjectResponse);

    if (updateProjectResponse.getReturnCode() != 0
            || !"OK".equals(updateProjectResponse.getReturnMessage())) {
      throw new JaggaerApplicationException(updateProjectResponse.getReturnCode(),
              updateProjectResponse.getReturnMessage());
    }


    return updateProjectResponse;
  }

  /**
   * Create or update an Rfx (Event).
   *
   * @param rfx
   * @return
   */
  public CreateUpdateRfxResponse createUpdateRfx(final Rfx rfx, final OperationCode operationCode) {

    log.info("Start calling Jaggaer API to create or update rfx, Rfx Id: {}", rfx.getRfxSetting().getRfxId());
    final var createRfxResponse =
            ofNullable(jaggaerWebClient.post().uri(jaggaerAPIConfig.getCreateRfx().get(ENDPOINT))
                    .bodyValue(new CreateUpdateRfx(operationCode, rfx)).retrieve()
                    .bodyToMono(CreateUpdateRfxResponse.class)
                    .block(ofSeconds(jaggaerAPIConfig.getTimeoutDuration())))
                    .orElseThrow(() -> new JaggaerApplicationException(INTERNAL_SERVER_ERROR.value(),
                            "Unexpected error updating Rfx"));
    log.info("Finish calling Jaggaer API to create or update rfx, Rfx Id: {} ", rfx.getRfxSetting().getRfxId());

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
   * @param externalEventId
   * @return
   * @deprecated This method (or rather endpoint that it calls) is non-performant and should be
   * replaced with calls through {@link #searchRFx(Set)} instead where possible
   */
  @Deprecated
  public ExportRfxResponse getRfx(final String externalEventId) {

    final var exportRfxUri = jaggaerAPIConfig.getExportRfx().get(ENDPOINT);
    log.info("Start calling Jaggaer API to get rfx, Rfx Id: {}", externalEventId);
    final var rfxResponse = getExportRfxResponse(externalEventId, exportRfxUri);
    log.info("Finish calling Jaggaer API to get rfx, Rfx Id: {}", externalEventId);
    return rfxResponse;
  }


  public ExportRfxResponse getRfxWithEmailRecipients(final String externalEventId) {
    //TODO: This can be a candidate for cache
    final var exportRfxUri = jaggaerAPIConfig.getExportRfxWithEmailRecipients().get(ENDPOINT);
    log.info("Start calling Jaggaer API to get rfx with Email Recipients, Rfx Id: {}", externalEventId);
    final var rfxResponse = getExportRfxResponse(externalEventId, exportRfxUri);
    log.info("Finish calling Jaggaer API to get rfx with Email Recipients, Rfx Id: {}", externalEventId);
    return rfxResponse;
  }

  public ExportRfxResponse getRfxWithSuppliers(final String externalEventId) {

    final var exportRfxUri = jaggaerAPIConfig.getExportRfxWithSuppliers().get(ENDPOINT);
    log.info("Start calling Jaggaer API to get rfx with suppliers, Rfx Id: {}", externalEventId);
    final var rfxResponse = getExportRfxResponse(externalEventId, exportRfxUri);
    log.info("Finish calling Jaggaer API to get rfx with suppliers, Rfx Id: {}", externalEventId);
    return rfxResponse;
  }

  public ExportRfxResponse getRfxWithSuppliersOffersAndResponseCounters(final String externalEventId) {

    final var exportRfxUri = jaggaerAPIConfig.getExportRfxWithSuppliersOffersAndResponseCounters().get(ENDPOINT);
    log.info("Start calling Jaggaer API to get rfx with suppliers offers and response counters, Rfx Id: {}", externalEventId);
    final var rfxResponse = getExportRfxResponse(externalEventId, exportRfxUri);
    log.info("Finish calling Jaggaer API to get rfx with suppliers offers and response counters, Rfx Id: {}", externalEventId);
    return rfxResponse;
  }

  public ExportRfxResponse getRfxWithWithBuyerAndSellerAttachments(final String externalEventId) {

    final var exportRfxUri = jaggaerAPIConfig.getExportRfxWithBuyerAndSellerAttachments().get(ENDPOINT);
    log.info("Start calling Jaggaer API to get rfx with buyer and seller attachments, Rfx Id: {}", externalEventId);
    final var rfxResponse = getExportRfxResponse(externalEventId, exportRfxUri);
    log.info("Finish calling Jaggaer API to get rfx with buyer and seller attachments, Rfx Id: {}", externalEventId);
    return rfxResponse;
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

    log.info("Start calling Jaggaer API to search rfx, Rfx Ids: {}", rfxIds);
    var searchRfxResponse = webclientWrapper
            .getOptionalResource(SearchRfxsResponse.class, jaggaerWebClient,
                    jaggaerAPIConfig.getTimeoutDuration(), searchRfxUri, rfxIds)
            .orElseThrow(() -> new JaggaerApplicationException(INTERNAL_SERVER_ERROR.value(),
                    "Unexpected error searching rfxs"));
    log.info("Finish calling Jaggaer API to search rfx, Rfx Ids: {}", rfxIds);

    if (searchRfxResponse.getReturnCode() == 0) {
      return searchRfxResponse.getDataList().getRfx();
    }
    throw new JaggaerApplicationException(INTERNAL_SERVER_ERROR.value(),
            "Unexpected error searching rfxs");
  }

  public Set<ExportRfxResponse> searchRFxWithComponents(final Set<String> externalEventIds,
                                                        final Set<String> components) {

    var searchRfxUri = jaggaerAPIConfig.getSearchRfxSummaryWithComponents().get(ENDPOINT);
    var rfxIds = externalEventIds.stream().collect(Collectors.joining(","));
    var componentFilters = components.stream().collect(Collectors.joining(";"));

    log.info("Start calling Jaggaer API to search rfx with components. Rfx Id: {}, Components: {}", rfxIds, componentFilters);
    var searchRfxResponse = webclientWrapper
            .getOptionalResource(SearchRfxsResponse.class, jaggaerWebClient,
                    jaggaerAPIConfig.getTimeoutDuration(), searchRfxUri, rfxIds, componentFilters)
            .orElseThrow(() -> new JaggaerApplicationException(INTERNAL_SERVER_ERROR.value(),
                    "Unexpected error searching rfxs"));
    log.info("Finish calling Jaggaer API to search rfx with components. Rfx Id: {}, Components: {}", rfxIds, componentFilters);

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
    log.info("Start calling Jaggaer API to get rfx by component, Rfx Id: {}", externalEventId);
    final var rfxResponse = ofNullable(jaggaerWebClient.get().uri(rfxUri, externalEventId, componentFilters).retrieve()
            .bodyToMono(ExportRfxResponse.class)
            .block(ofSeconds(jaggaerAPIConfig.getTimeoutDuration())))
            .orElseThrow(() -> new JaggaerApplicationException(INTERNAL_SERVER_ERROR.value(),
                    "Unexpected error retrieving rfx"));
    log.info("Finish calling Jaggaer API to get rfx by component, Rfx Id: {}", externalEventId);
    return rfxResponse;
  }


  /**
   * Create or update a company and/or sub-users
   *
   * @param createUpdateCompanyRequest
   * @return response containing code, message and bravoId of company
   */
  public CreateUpdateCompanyResponse createUpdateCompany(
          final CreateUpdateCompanyRequest createUpdateCompanyRequest) {
    log.info("Start calling Jaggaer API to create or update company, Request: {}", createUpdateCompanyRequest);
    final var createUpdateCompanyResponse = webclientWrapper.postData(createUpdateCompanyRequest,
            CreateUpdateCompanyResponse.class, jaggaerWebClient, jaggaerAPIConfig.getTimeoutDuration(),
            jaggaerAPIConfig.getCreateUpdateCompany().get(ENDPOINT));
    log.info("Start calling Jaggaer API to create or update company, Response: {}", createUpdateCompanyResponse);

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

    if (multipartFile.getOriginalFilename() == null) {
      throw new IllegalArgumentException("No filename specified for upload document attachment");
    }

    final MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
    parts.add("data", rfx);
    parts.add(multipartFile.getOriginalFilename(), multipartFile.getResource());

    log.info("Start calling Jaggaer API to upload document. File name: {}", multipartFile.getOriginalFilename());
    final var response =
            ofNullable(jaggaerWebClient.post().uri(jaggaerAPIConfig.getCreateRfx().get(ENDPOINT))
                    .contentType(MediaType.MULTIPART_FORM_DATA).body(BodyInserters.fromMultipartData(parts))
                    .retrieve().bodyToMono(CreateUpdateRfxResponse.class).block())
                    .orElseThrow(() -> new JaggaerApplicationException(
                            "Upload attachment from Jaggaer returned a null response: rfxId:"
                                    + rfx.getRfx().getRfxSetting().getRfxId()));
    log.info("Finish calling Jaggaer API to upload document. File name: {}", multipartFile.getOriginalFilename());

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
    log.info("Start calling Jaggaer API to get attachment, File name: {}", fileName);
    final var response = jaggaerWebClient.get().uri(getAttachmentUri, fileId, fileName)
            .header(ACCEPT, MediaType.APPLICATION_OCTET_STREAM_VALUE).retrieve().toEntity(byte[].class)
            .block();
    log.info("Finish calling Jaggaer API to get attachment, File name: {}", fileName);


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

    log.info("Start calling Jaggaer API to publish rfx, Rfx Id: {}", publishRfx.getRfxId());
    final var publishRfxResponse = webclientWrapper.postData(publishRfx, PublishRfxResponse.class,
            jaggaerWebClient, jaggaerAPIConfig.getTimeoutDuration(), publishRfxEndpoint);
    log.info("Finish calling Jaggaer API to publish rfx, Rfx Id: {}", publishRfx.getRfxId());

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
   * @param jaggaerUserId
   * @return ProjectList
   */
  public ProjectListResponse getProjectList(final String jaggaerUserId) {
    final var projectListUri = jaggaerAPIConfig.getGetProjectList().get(ENDPOINT);
    final var filters = "projectOwnerId==" + jaggaerUserId;

    log.info("Start calling Jaggaer API to get project list using project owner Id: {}", jaggaerUserId);
    final var projects = ofNullable(jaggaerWebClient.get().uri(projectListUri, filters).retrieve()
            .bodyToMono(ProjectListResponse.class)
            .block(ofSeconds(jaggaerAPIConfig.getTimeoutDuration())))
            .orElseThrow(() -> new JaggaerApplicationException(INTERNAL_SERVER_ERROR.value(),
                    "Unexpected error retrieving projects"));
    log.info("Finish calling Jaggaer API to get project list using project owner Id: {}", jaggaerUserId);
    return projects;
  }

  public MessagesResponse getMessages(final String externalEventId, final Integer pageSize) {
    final var messagesUrl = jaggaerAPIConfig.getGetMessages().get(ENDPOINT);
    final var start = pageSize > 1 ? pageSize + 1 : 1;
    final var filters = "objectReferenceCode==" + externalEventId;

    log.info("Start calling Jaggaer API to get messages, Event Id: {}", externalEventId);
    final var messagesResponse = ofNullable(jaggaerWebClient.get().uri(messagesUrl, filters, MESSAGE_PARAMS, start)
            .retrieve().bodyToMono(MessagesResponse.class)
            .block(ofSeconds(jaggaerAPIConfig.getTimeoutDuration())))
            .orElseThrow(() -> new JaggaerApplicationException(INTERNAL_SERVER_ERROR.value(),
                    "Unexpected error retrieving messages"));
    log.info("Finish calling Jaggaer API to get messages, Event Id: {}", externalEventId);
    return messagesResponse;
  }

  public Message getMessage(final String messageId) {
    final var messagesUrl = jaggaerAPIConfig.getGetMessage().get(ENDPOINT);
    log.info("Start calling Jaggaer API to get message, Message Id: {}", messageId);
    final var messageResponse = ofNullable(jaggaerWebClient.get().uri(messagesUrl, messageId, MESSAGE_PARAMS).retrieve()
            .bodyToMono(Message.class)
            .block(ofSeconds(jaggaerAPIConfig.getTimeoutDuration())))
            .orElseThrow(() -> new JaggaerApplicationException(INTERNAL_SERVER_ERROR.value(),
                    "Unexpected error retrieving messages"));
    log.info("Finish calling Jaggaer API to get message, Message Id: {}", messageId);
    return messageResponse;
  }

  @Async
  public Future<MessageResponse> updateMessage(final MessageUpdate messageUpdate) {
    final var updateMessageUrl = jaggaerAPIConfig.getUpdateMessage().get(ENDPOINT);

    log.info("Start calling Jaggaer API to update message, Message Id: {}", messageUpdate.getMessageId());
    final var updateResponse = new AsyncResult<>(ofNullable(jaggaerWebClient.put().uri(updateMessageUrl).body(Mono.just(messageUpdate), MessageUpdate.class).retrieve()
            .bodyToMono(MessageResponse.class)
            .block(ofSeconds(jaggaerAPIConfig.getTimeoutDuration())))
            .orElseThrow(() -> new JaggaerApplicationException(INTERNAL_SERVER_ERROR.value(),
                    "Unexpected error retrieving messages")));
    log.info("Finish calling Jaggaer API to update message, Message Id: {}", messageUpdate.getMessageId());
    return updateResponse;
  }

  /**
   * Start Evaluation Rfx
   *
   * @param event
   * @param jaggaerUserId
   */
  public void startEvaluation(final ProcurementEvent event, final String jaggaerUserId) {
    final var startEvaluationRequest = getStartEvaluationRequest(event, jaggaerUserId);

    final var endPoint = jaggaerAPIConfig.getStartEvaluation().get(ENDPOINT);

    log.info("Start calling Jaggaer API to start evaluation, Rfx Id: {}", event.getExternalEventId());
    final var evaluationResponse =
            webclientWrapper.postData(startEvaluationRequest, WorkflowRfxResponse.class,
                    jaggaerWebClient, jaggaerAPIConfig.getTimeoutDuration(), endPoint);
    log.info("Finish calling Jaggaer API to start evaluation, Rfx Id: {}", event.getExternalEventId());

    log.debug("Start evaluation event response: {}", evaluationResponse);
  }

  private static RfxWorkflowRequest getStartEvaluationRequest(ProcurementEvent event, String jaggaerUserId) {
    return RfxWorkflowRequest.builder()
            .rfxId(event.getExternalEventId()).rfxReferenceCode(event.getExternalReferenceId())
            .operatorUser(OwnerUser.builder().id(jaggaerUserId).build()).build();
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

    Instant retrieveDocStart = Instant.now();

    this.uploadDocument(multipartFile, update);
    Instant retrieveDocEnd = Instant.now();

    log.info("JaggaerService : eventUploadDocument  : Total time taken to uploadDocument service for procID {} : eventId :{} , Filename : {},  Timetaken : {}  ", event.getProject().getId(), event.getEventID(), fileName,
            Duration.between(retrieveDocStart, retrieveDocEnd).toMillis());


  }

  /**
   * extend Rfx
   *
   * @param rfx
   * @param operationCode
   */
  public CreateUpdateRfxResponse extendRfx(final RfxRequest rfx,
                                           final OperationCode operationCode) {

    log.info("Start calling Jaggaer API to extend rfx, Rfx Id: {}", rfx.getRfxSetting().getRfxId());
    final var extendRfxResponse = webclientWrapper.postData(new ExtendEventRfx(operationCode, rfx),
            CreateUpdateRfxResponse.class, jaggaerWebClient, jaggaerAPIConfig.getTimeoutDuration(),
            jaggaerAPIConfig.getCreateRfx().get(ENDPOINT));
    log.info("Finish calling Jaggaer API to extend rfx, Rfx Id: {}", rfx.getRfxSetting().getRfxId());

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

    log.info("Start calling Jaggaer API to invalidate event, Rfx Id: {}", request.getRfxId());
    final var response = webclientWrapper.postData(request, WorkflowRfxResponse.class,
            jaggaerWebClient, jaggaerAPIConfig.getTimeoutDuration(), endPoint);
    log.info("Finish calling Jaggaer API to invalidate event, Rfx Id: {}", request.getRfxId());

    log.debug("Invalidate event response: {}", response);
  }

  /**
   * Get Project
   *
   * @param externalProjectId
   */
  public Project getProject(final String externalProjectId) {
    log.info("Start calling Jaggaer API to get project using project Id: {}", externalProjectId);
    final var project = ofNullable(jaggaerWebClient.get()
            .uri(jaggaerAPIConfig.getGetProject().get(ENDPOINT), externalProjectId).retrieve()
            .bodyToMono(Project.class).block(ofSeconds(jaggaerAPIConfig.getTimeoutDuration())))
            .orElseThrow(() -> new JaggaerApplicationException(INTERNAL_SERVER_ERROR.value(),
                    "Unexpected error retrieving project"));
    log.info("Finish calling Jaggaer API to get project using project Id: {}", externalProjectId);
    return project;
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

    log.info("Start calling Jaggaer API to award or pre-award rfx, Rfx Id: {}", event.getExternalEventId());
    final var response = webclientWrapper.postData(awardRequest, WorkflowRfxResponse.class,
            jaggaerWebClient, jaggaerAPIConfig.getTimeoutDuration(), endPoint);
    log.info("Finish calling Jaggaer API to award or pre-award rfx, Rfx Id: {}", event.getExternalEventId());

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
    final var completeTechnicalRequest = getStartEvaluationRequest(event, jaggaerUserId);

    final var endPoint = jaggaerAPIConfig.getCompleteTechnical().get(ENDPOINT);

    log.info("Start calling Jaggaer API to complete technical, Rfx Id: {}", event.getExternalEventId());
    final var response =
            webclientWrapper.postData(completeTechnicalRequest, WorkflowRfxResponse.class,
                    jaggaerWebClient, jaggaerAPIConfig.getTimeoutDuration(), endPoint);
    log.info("Finish calling Jaggaer API to complete technical, Rfx Id: {}", event.getExternalEventId());

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

    log.info("Start calling Jaggaer API to open envelope, Rfx Id: {}", event.getExternalEventId());
    final var envelopeResponse = webclientWrapper.postData(openEnvelopeRequest,
            WorkflowRfxResponse.class, jaggaerWebClient, jaggaerAPIConfig.getTimeoutDuration(),
            jaggaerAPIConfig.getOpenEnvelope().get(ENDPOINT));
    log.info("Finish calling Jaggaer API to open envelope, Rfx Id: {}", event.getExternalEventId());

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
    log.info("Start calling Jaggaer API to create or reply message, Buyer Id: {}", messageRequest.getOperatorUser().getId());
    final var messageResponse = webclientWrapper.postData(messageRequest, MessageResponse.class,
            jaggaerWebClient, jaggaerAPIConfig.getTimeoutDuration(), endPoint);
    log.info("Finish calling Jaggaer API to create or reply message, Buyer Id: {}", messageRequest.getOperatorUser().getId());
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

    log.info("Start calling Jaggaer API to create or update scores, Rfx reference code: {}", scoringRequest.getRfxReferenceCode());
    final var scoreResponse = webclientWrapper.postData(scoringRequest, ScoringResponse.class,
            jaggaerWebClient, jaggaerAPIConfig.getTimeoutDuration(), endPoint);
    log.info("Finish calling Jaggaer API to create or update scores, Rfx reference code: {}", scoringRequest.getRfxReferenceCode());

    log.debug("Create-update scoring response: {}", scoreResponse);
    if (scoreResponse.getReturnCode() != 0
            || !Constants.OK_MSG.equals(scoreResponse.getReturnMessage())) {
      log.error(scoreResponse.toString());
    }
    return scoreResponse;
  }

  private ExportRfxResponse getExportRfxResponse(String externalEventId, String exportRfxUri) {
    return ofNullable(jaggaerWebClient.get().uri(exportRfxUri, externalEventId).retrieve()
            .bodyToMono(ExportRfxResponse.class)
            .block(ofSeconds(jaggaerAPIConfig.getTimeoutDuration())))
            .orElseThrow(() -> new JaggaerApplicationException(INTERNAL_SERVER_ERROR.value(),
                    "Unexpected error retrieving rfx"));
  }
}