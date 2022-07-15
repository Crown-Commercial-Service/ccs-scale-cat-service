package uk.gov.crowncommercial.dts.scale.cat.service.documentupload;

import static java.lang.String.format;
import static java.util.Optional.ofNullable;
import static uk.gov.crowncommercial.dts.scale.cat.config.DocumentUploadAPIConfig.KEY_URI_TEMPLATE;
import java.io.EOFException;
import java.io.IOException;
import java.time.Duration;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import org.apache.commons.io.IOUtils;
import org.apache.tika.Tika;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import reactor.util.retry.Retry;
import uk.gov.crowncommercial.dts.scale.cat.config.Constants;
import uk.gov.crowncommercial.dts.scale.cat.config.DocumentUploadAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.config.paas.AWSS3Service;
import uk.gov.crowncommercial.dts.scale.cat.exception.DocumentUploadApplicationException;
import uk.gov.crowncommercial.dts.scale.cat.exception.TendersDBDataException;
import uk.gov.crowncommercial.dts.scale.cat.model.DocumentKey;
import uk.gov.crowncommercial.dts.scale.cat.model.documentupload.DocumentStatus;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.DocumentUpload;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.Timestamps;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.VirusCheckStatus;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.DocumentAudienceType;
import uk.gov.crowncommercial.dts.scale.cat.repo.DocumentUploadRepo;
import uk.gov.crowncommercial.dts.scale.cat.repo.ProcurementEventRepo;
import uk.gov.crowncommercial.dts.scale.cat.service.WebclientWrapper;

/**
 * Handles interactions with the external Document Upload Service (including its S3 bucket) and the
 * Tenders Document Store (database record + S3 object)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentUploadService {

  static final String TENDERS_S3_OBJECT_KEY_FORMAT = "/%s/%s/%s";
  static final Tika TIKA = new Tika();
  static final long DEFAULT_SIZE_VALIDATION = 1000;

  private final AmazonS3 documentUploadS3Client;
  private final AmazonS3 tendersS3Client;
  private final AWSS3Service tendersS3Service;
  private final DocumentUploadAPIConfig apiConfig;
  private final WebClient docUploadSvcUploadWebclient;
  private final WebClient docUploadSvcGetWebclient;
  private final WebclientWrapper webclientWrapper;
  private final DocumentUploadRepo documentUploadRepo;
  private final ProcurementEventRepo procurementEventRepo;

  static final boolean isWebClientRequestException(final Throwable throwable) {
    log.error("Caught error in DUS webclient:", throwable);
    return throwable instanceof WebClientRequestException webClientRequestException
        && webClientRequestException.getCause() instanceof EOFException;
  }

  /**
   * Upload a new document for processing by the Document Upload Service
   *
   * @param event
   * @param multipartFile
   * @param audience
   * @param documentDescription
   * @param principal
   * @return
   */
  public DocumentUpload uploadDocument(final ProcurementEvent event,
      final MultipartFile multipartFile, final DocumentAudienceType audience,
      final String documentDescription, final String principal) {

    var mimetype = TIKA.detect(multipartFile.getOriginalFilename());
    final MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
    parts.add("typeValidation[]", mimetype);
    parts.add("sizeValidation", multipartFile.getSize() + 1000);
    parts.add("documentFile", multipartFile.getResource());

    final var documentStatus = ofNullable(
        docUploadSvcUploadWebclient.post().uri(apiConfig.getPostDocument().get(KEY_URI_TEMPLATE))
            .contentType(MediaType.MULTIPART_FORM_DATA).body(BodyInserters.fromMultipartData(parts))
            .retrieve().bodyToMono(DocumentStatus.class)
            .retryWhen(Retry
                .fixedDelay(Constants.WEBCLIENT_DEFAULT_RETRIES,
                    Duration.ofSeconds(Constants.WEBCLIENT_DEFAULT_DELAY))
                .filter(DocumentUploadService::isWebClientRequestException))
            .block()).orElseThrow(() -> new DocumentUploadApplicationException(""));

    // Create document ID based on hash of the external ID
    var docKey = new DocumentKey(Math.abs(documentStatus.getId().hashCode()),
        multipartFile.getOriginalFilename(), audience);

    var documentUpload = DocumentUpload.builder().procurementEvent(event)
        .documentId(docKey.getDocumentId()).externalDocumentId(documentStatus.getId())
        .externalStatus(VirusCheckStatus.PROCESSING).audience(audience)
        .size(multipartFile.getSize()).documentDescription(documentDescription).mimetype(mimetype)
        .timestamps(Timestamps.createTimestamps(principal)).build();
    event.getDocumentUploads().add(documentUpload);
    procurementEventRepo.save(event);

    return documentUpload;
  }

  /**
   * Delete a document from Tenders document store
   *
   * @param event
   * @param documentId
   */
  public void deleteDocument(final DocumentUpload documentUpload) {
    var event = documentUpload.getProcurementEvent();

    // Document should only exist in Tenders S3 if safe
    if (documentUpload.getExternalStatus() == VirusCheckStatus.SAFE) {
      tendersS3Client.deleteObject(tendersS3Service.getCredentials().getBucketName(),
          tendersS3ObjectKey(event.getProject().getId(), event.getEventID(),
              documentUpload.getDocumentId()));
    }
    documentUploadRepo.delete(documentUpload);
    event.getDocumentUploads().remove(documentUpload);
    procurementEventRepo.save(event);
  }

  /**
   * Retrieve the given document from the Tenders document store
   *
   * @param event
   * @param documentUpload
   * @param principal
   * @return
   */
  @SneakyThrows
  public byte[] retrieveDocument(final DocumentUpload documentUpload, final String principal) {
    var documentId = documentUpload.getDocumentId();
    var event = documentUpload.getProcurementEvent();
    var tendersS3ObjectKey =
        tendersS3ObjectKey(event.getProject().getId(), event.getEventID(), documentId);

    switch (documentUpload.getExternalStatus()) {
      case SAFE:
        // Get document from Tenders S3
        return getFromTendersS3(tendersS3ObjectKey);

      case PROCESSING:
        // Invoke processing of remote S3 / throw error if still processing / unsafe?
        processDocuments(Set.of(documentUpload), principal);
        if (documentUpload.getExternalStatus() == VirusCheckStatus.SAFE) {
          return getFromTendersS3(tendersS3ObjectKey);
        } else {
          throw new DocumentUploadApplicationException(
              "Requested document is still being processed and is unavilable to download");
        }

      case UNSAFE:
        throw new DocumentUploadApplicationException(
            "Requested document was found to contain threats (viruses) and is unavilable to download");

      default:
        throw new TendersDBDataException(
            format("Document upload has unknown state [%s] in Tenders DB",
                documentUpload.getExternalStatus()));
    }
  }

  /**
   * Process a collection of (unprocessed) document uploads. For each document, fitst check if the
   * document has already been processed (e.g. by a different thread) and if not, get the processing
   * status from the external document upload service and action accordingly - if SAFE, copy the
   * document (object) from the remote S3 bucket into the Tenders S3 bucket. If UNSAFE, do not copy
   * but in both scenarios update the TDB document upload record
   *
   * @param unprocessedDocuments
   * @param principal
   */
  void processDocuments(final Collection<DocumentUpload> unprocessedDocuments,
      final String principal) {
    unprocessedDocuments.stream().forEach(unprocessedDocUpload -> {

      // Ensure processing of each document takes place only once
      // TODO: Replace with Blocking queue + timeout to avoid deadlocks
      synchronized (unprocessedDocUpload) {
        // Pre-check that the document hasn't just been processed by separate thread
        if (unprocessedDocUpload.getExternalStatus() != VirusCheckStatus.PROCESSING) {
          log.debug("Document [{}] already processed - skipping",
              unprocessedDocUpload.getDocumentId());
          return;
        }

        var documentStatusResponse = webclientWrapper.getOptionalResource(DocumentStatus.class,
            docUploadSvcGetWebclient, apiConfig.getTimeoutDuration(),
            apiConfig.getGetDocumentRecord().get(KEY_URI_TEMPLATE),
            unprocessedDocUpload.getExternalDocumentId());

        documentStatusResponse.ifPresentOrElse(documentStatus -> {

          if (Objects.equals(apiConfig.getDocumentStateSafe(), documentStatus.getState())) {
            copyDocumentFromRemoteS3(unprocessedDocUpload, documentStatus);
            unprocessedDocUpload.setExternalStatus(VirusCheckStatus.SAFE);
            unprocessedDocUpload.setTimestamps(
                Timestamps.updateTimestamps(unprocessedDocUpload.getTimestamps(), principal));
          } else if (Objects.equals(apiConfig.getDocumentStateUnsafe(),
              documentStatus.getState())) {
            unprocessedDocUpload.setExternalStatus(VirusCheckStatus.UNSAFE);
            unprocessedDocUpload.setTimestamps(
                Timestamps.updateTimestamps(unprocessedDocUpload.getTimestamps(), principal));
            log.debug("Unsafe document identified, ID: [{}], event: [{}]",
                unprocessedDocUpload.getId(),
                unprocessedDocUpload.getProcurementEvent().getEventID());
          }
          documentUploadRepo.save(unprocessedDocUpload);

        }, () -> log.error("Unable to get status from doc upload service for document ID: [{}]",
            unprocessedDocUpload.getDocumentId()));
      }
    });
  }

  private void copyDocumentFromRemoteS3(final DocumentUpload unprocessedDocUpload,
      final DocumentStatus documentStatus) {
    var remoteS3Object = documentUploadS3Client.getObject(apiConfig.getS3Bucket(),
        documentStatus.getDocumentFile().getUrl());

    var tendersS3ObjectKey =
        tendersS3ObjectKey(unprocessedDocUpload.getProcurementEvent().getProject().getId(),
            unprocessedDocUpload.getProcurementEvent().getEventID(),
            unprocessedDocUpload.getDocumentId());

    var s3ObjectMetadata = new ObjectMetadata();
    s3ObjectMetadata.setContentType(unprocessedDocUpload.getMimetype());
    s3ObjectMetadata.setContentLength(unprocessedDocUpload.getSize());

    log.debug(
        "Copying object: [{}] from remote S3 bucket: [{}] to object: [{}] in Tenders S3 bucket: [{}]",
        documentStatus.getDocumentFile().getUrl(), apiConfig.getS3Bucket(), tendersS3ObjectKey,
        tendersS3Service.getCredentials().getBucketName());

    tendersS3Client.putObject(tendersS3Service.getCredentials().getBucketName(), tendersS3ObjectKey,
        remoteS3Object.getObjectContent(), s3ObjectMetadata);
  }

  private byte[] getFromTendersS3(final String tendersS3ObjectKey) throws IOException {
    var tendersS3Object = tendersS3Client
        .getObject(tendersS3Service.getCredentials().getBucketName(), tendersS3ObjectKey);
    return IOUtils.toByteArray(tendersS3Object.getObjectContent());
  }

  private String tendersS3ObjectKey(final Integer projectId, final String eventId,
      final String documentId) {
    return String.format(TENDERS_S3_OBJECT_KEY_FORMAT, projectId, eventId, documentId);
  }

}
