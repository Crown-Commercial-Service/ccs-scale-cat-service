package uk.gov.crowncommercial.dts.scale.cat.service;

import static java.time.Duration.ofSeconds;
import static java.util.Optional.ofNullable;
import static org.springframework.http.HttpHeaders.ACCEPT;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig.ENDPOINT;
import java.time.Duration;
import java.util.Objects;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.config.Constants;
import uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.exception.JaggaerApplicationException;
import uk.gov.crowncommercial.dts.scale.cat.model.DocumentAttachment;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.PublishDates;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.*;

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

  /**
   * Create or update a Project.
   *
   * @param createUpdateProject
   * @return
   */
  public CreateUpdateProjectResponse createUpdateProject(
      final CreateUpdateProject createUpdateProject) {

    var updateProjectResponse =
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

    var createRfxResponse =
        ofNullable(jaggaerWebClient.post().uri(jaggaerAPIConfig.getCreateRfx().get(ENDPOINT))
            .bodyValue(new CreateUpdateRfx(operationCode, rfx)).retrieve()
            .bodyToMono(CreateUpdateRfxResponse.class)
            .block(ofSeconds(jaggaerAPIConfig.getTimeoutDuration())))
                .orElseThrow(() -> new JaggaerApplicationException(INTERNAL_SERVER_ERROR.value(),
                    "Unexpected error updating Rfx"));

    if (createRfxResponse.getReturnCode() != 0
        || !Constants.JAGGAER_GET_OK_MSG.equals(createRfxResponse.getReturnMessage())) {
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
   * @param eventId
   * @return
   */
  public ExportRfxResponse getRfx(final String eventId) {

    var exportRfxUri = jaggaerAPIConfig.getExportRfx().get(ENDPOINT);
    return ofNullable(jaggaerWebClient.get().uri(exportRfxUri, eventId).retrieve()
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
    var createUpdateCompanyResponse = webclientWrapper.postData(createUpdateCompanyRequest,
        CreateUpdateCompanyResponse.class, jaggaerWebClient, jaggaerAPIConfig.getTimeoutDuration(),
        jaggaerAPIConfig.getCreateUpdateCompany().get(ENDPOINT));

    log.debug("Create update company response: {}", createUpdateCompanyResponse);

    if (!"0".equals(createUpdateCompanyResponse.getReturnCode())) {
      throw new JaggaerApplicationException(createUpdateCompanyResponse.getReturnCode(),
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

    MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
    parts.add("data", rfx);
    parts.add(multipartFile.getOriginalFilename(), multipartFile.getResource());

    jaggaerWebClient.post().uri(jaggaerAPIConfig.getCreateRfx().get(ENDPOINT))
        .contentType(MediaType.MULTIPART_FORM_DATA).body(BodyInserters.fromMultipartData(parts))
        .retrieve().bodyToMono(String.class).block();
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

    var getAttachmentUri = jaggaerAPIConfig.getGetAttachment().get(ENDPOINT);
    var response = jaggaerWebClient.get().uri(getAttachmentUri, fileId, fileName)
        .header(ACCEPT, MediaType.APPLICATION_OCTET_STREAM_VALUE).retrieve().toEntity(byte[].class)
        .block();

    if (response == null) {
      throw new JaggaerApplicationException(
          "Get attachment from Jaggaer returned a null response: fileId:" + fileId + ", fileName: "
              + fileName);
    }
    return new DocumentAttachment(response.getBody(), response.getHeaders().getContentType());
  }

  /**
   * Transitions an existing Rfx to status "Running" via the Rfx workflow API
   *
   * @param procId
   * @param eventId
   * @throws JsonProcessingException
   */
  public void publishRfx(final ProcurementEvent event, final PublishDates publishDates,
      final String jaggaerUserId) {

    // TODO: What do we do with `publishDate.startDate`, if supplied?

    var publishRfx = PublishRfx.builder().rfxId(event.getExternalEventId())
        .rfxReferenceCode(event.getExternalReferenceId())
        .operatorUser(OwnerUser.builder().id(jaggaerUserId).build())
        .newClosingDate(publishDates.getEndDate().toInstant()).build();

    var publishRfxEndpoint = jaggaerAPIConfig.getPublishRfx().get(ENDPOINT);

    var publishRfxResponse = webclientWrapper.postData(publishRfx, PublishRfxResponse.class,
        jaggaerWebClient, jaggaerAPIConfig.getTimeoutDuration(), publishRfxEndpoint);

    log.debug("Publish event response: {}", publishRfxResponse);

    if (!Objects.equals(0, publishRfxResponse.getReturnCode())
        || !Constants.JAGGAER_GET_OK_MSG.equals(publishRfxResponse.getReturnMessage())) {
      throw new JaggaerApplicationException(publishRfxResponse.getReturnCode(),
          publishRfxResponse.getReturnMessage());
    }
  }

}
