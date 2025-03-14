package uk.gov.crowncommercial.dts.scale.cat.service.documentupload;

import static java.lang.String.format;
import static java.util.Optional.ofNullable;
import static uk.gov.crowncommercial.dts.scale.cat.config.DocumentUploadAPIConfig.KEY_URI_TEMPLATE;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.io.IOUtils;
import org.apache.tika.Tika;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StopWatch;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.SdkClientException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.S3Object;
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
        .size(multipartFile.getSize()).documentDescription(documentDescription)
        .mimetype(mimetype).timestamps(Timestamps.createTimestamps(principal)).build();
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
   * @param documentUpload
   * @param principal
   * @return
   */
  @SneakyThrows
  public byte[] retrieveDocument(final DocumentUpload documentUpload, final String principal) {
    return IOUtils.toByteArray(retrieveDocumentStream(documentUpload, principal));
  }

  @SneakyThrows
  public InputStream retrieveDocumentStream(final DocumentUpload documentUpload, final String principal) {
    var documentId = documentUpload.getDocumentId();
    var event = documentUpload.getProcurementEvent();
    var tendersS3ObjectKey =
            tendersS3ObjectKey(event.getProject().getId(), event.getEventID(), documentId);
    switch (documentUpload.getExternalStatus()) {
      case SAFE:
        // Get document from Tenders S3
        log.info("Getting Document from TenderS3 {} ", documentId);
        Instant retrieveDocStart = Instant.now();
        var tenderDbDoc= getFromTendersS3Stream(tendersS3ObjectKey, documentId, principal);
        Instant retrieveDocEnd = Instant.now();
        log.info("retrieveDocument : Total time taken to retrieveDocument service for procID {} : eventId :{} , Timetaken : {}  ",
              documentUpload.getProcurementEvent().getProject().getId(),documentUpload.getProcurementEvent().getEventID(),
              Duration.between(retrieveDocStart,retrieveDocEnd).toMillis());
        return tenderDbDoc;
      case PROCESSING:
        // Invoke processing of remote S3 / throw error if still processing / unsafe?
        processDocuments(Set.of(documentUpload), principal);
        if (documentUpload.getExternalStatus() == VirusCheckStatus.SAFE) {
          return getFromTendersS3Stream(tendersS3ObjectKey, documentId, principal);
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
//  void processDocuments(final Collection<DocumentUpload> unprocessedDocuments,
//      final String principal) {
//    unprocessedDocuments.forEach(unprocessedDocUpload -> {
//
//      // Ensure processing of each document takes place only once
//      // TODO: Replace with Blocking queue + timeout to avoid deadlocks
//      synchronized (unprocessedDocUpload) {
//        // Pre-check that the document hasn't just been processed by separate thread
//        if (unprocessedDocUpload.getExternalStatus() != VirusCheckStatus.PROCESSING) {
//          log.debug("Document [{}] already processed - skipping",
//              unprocessedDocUpload.getDocumentId());
//          return;
//        }
//
//        var documentStatusResponse = webclientWrapper.getOptionalResource(DocumentStatus.class,
//            docUploadSvcGetWebclient, apiConfig.getTimeoutDuration(),
//            apiConfig.getGetDocumentRecord().get(KEY_URI_TEMPLATE),
//            unprocessedDocUpload.getExternalDocumentId());
//
//        documentStatusResponse.ifPresentOrElse(documentStatus -> {
//
//          if (Objects.equals(apiConfig.getDocumentStateSafe(), documentStatus.getState())) {
//            copyDocumentFromRemoteS3(unprocessedDocUpload, documentStatus);
//            unprocessedDocUpload.setExternalStatus(VirusCheckStatus.SAFE);
//            unprocessedDocUpload.setTimestamps(
//                Timestamps.updateTimestamps(unprocessedDocUpload.getTimestamps(), principal));
//          } else if (Objects.equals(apiConfig.getDocumentStateUnsafe(),
//              documentStatus.getState())) {
//            unprocessedDocUpload.setExternalStatus(VirusCheckStatus.UNSAFE);
//            unprocessedDocUpload.setTimestamps(
//                Timestamps.updateTimestamps(unprocessedDocUpload.getTimestamps(), principal));
//            log.debug("Unsafe document identified, ID: [{}], event: [{}]",
//                unprocessedDocUpload.getId(),
//                unprocessedDocUpload.getProcurementEvent().getEventID());
//          }
//          documentUploadRepo.save(unprocessedDocUpload);
//
//        }, () -> log.error("Unable to get status from doc upload service for document ID: [{}]",
//            unprocessedDocUpload.getDocumentId()));
//      }
//    });
//  }


  void processDocuments(final Collection<DocumentUpload> unprocessedDocuments, final String principal) {
    if (unprocessedDocuments.isEmpty()) {
      return;
    }
    // Start polling for documents that remain in PROCESSING state
    System.out.println("Start polling for documents that remain in PROCESSING state");
    ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    System.out.println("Create a reference list that can be modified within the task");
    List<DocumentUpload> remainingDocuments = new ArrayList<>(unprocessedDocuments);
    // Create an AtomicReference to hold the future
    System.out.println("Create an AtomicReference to hold the future");
    AtomicReference<ScheduledFuture<?>> futureRef = new AtomicReference<>();

    // Track if we've timed out
    System.out.println("Track if we've timed out");
    AtomicBoolean hasTimedOut = new AtomicBoolean(false);

    // Schedule the polling task
    System.out.println("Schedule the polling task");
    ScheduledFuture<?> pollingTask = scheduler.scheduleAtFixedRate(() -> {
      try {
        // If we've timed out, don't process any more documents
        System.out.println("If we've timed out, don't process any more documents");
        if (hasTimedOut.get()) {
          ScheduledFuture<?> future = futureRef.get();
          if (future != null) {
            future.cancel(false);
          }
          return;
        }

        // Process any documents still in PROCESSING state
        System.out.println("Process any documents still in PROCESSING state");
        Iterator<DocumentUpload> iterator = remainingDocuments.iterator();
        while (iterator.hasNext()) {
          DocumentUpload doc = iterator.next();
           // Skip if no longer in PROCESSING state
          System.out.println("Skip if no longer in PROCESSING state");
          if (doc.getExternalStatus() != VirusCheckStatus.PROCESSING) {
            iterator.remove();
            continue;
          }
          // Check status from external service
          System.out.println("Check status from external service");
          var documentStatusResponse = webclientWrapper.getOptionalResource(
                  DocumentStatus.class,
                  docUploadSvcGetWebclient,
                  apiConfig.getTimeoutDuration(),
                  apiConfig.getGetDocumentRecord().get(KEY_URI_TEMPLATE),
                  doc.getExternalDocumentId());

          documentStatusResponse.ifPresent(documentStatus -> {
            if (Objects.equals(apiConfig.getDocumentStateSafe(), documentStatus.getState())) {
              copyDocumentFromRemoteS3(doc, documentStatus);
              doc.setExternalStatus(VirusCheckStatus.SAFE);
              doc.setTimestamps(Timestamps.updateTimestamps(doc.getTimestamps(), principal));
              documentUploadRepo.save(doc);
              iterator.remove();
            } else if (Objects.equals(apiConfig.getDocumentStateUnsafe(), documentStatus.getState())) {
              doc.setExternalStatus(VirusCheckStatus.UNSAFE);
              doc.setTimestamps(Timestamps.updateTimestamps(doc.getTimestamps(), principal));
              documentUploadRepo.save(doc);
              iterator.remove();
            }
            // If still processing, keep in the list for next poll
            System.out.println("If still processing, keep in the list for next poll");
          });
        }

          // If all documents are processed, cancel the polling task
        System.out.println("If all documents are processed, cancel the polling task");
        if (remainingDocuments.isEmpty()) {
          ScheduledFuture<?> future = futureRef.get();
          if (future != null) {
            future.cancel(false);
          }
          scheduler.shutdown();
        }
      } catch (Exception e) {
        log.error("Error during document status polling", e);
      }
    }, 2, 2, TimeUnit.SECONDS);

    //Store the future in the AtomicReference
    System.out.println("Store the future in the AtomicReference");
    futureRef.set(pollingTask);

    // Add a timeout to prevent infinite polling
    System.out.println("Add a timeout to prevent infinite polling");
    scheduler.schedule(() -> {
      hasTimedOut.set(true);

      ScheduledFuture<?> future = futureRef.get();
      if (future != null) {
        future.cancel(false);
      }

      if (!remainingDocuments.isEmpty()) {
        log.warn("{} documents remained in PROCESSING state after timeout", remainingDocuments.size());

        //Just log the IDs for later investigation
        remainingDocuments.forEach(doc -> {
          log.warn("Document ID [{}] remained in PROCESSING state after timeout", doc.getDocumentId());
        });
      }

      scheduler.shutdown();

      // Ensure scheduler is properly terminated
      System.out.println("Ensure scheduler is properly terminated");
      try {
        if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
          scheduler.shutdownNow();
        }
      } catch (InterruptedException e) {
        scheduler.shutdownNow();
        Thread.currentThread().interrupt();
      }
    }, 30, TimeUnit.SECONDS); // Total timeout of 30 seconds
    System.out.println("Total timeout of 30 seconds");

    // Initial processing of all documents
    System.out.println("Initial processing of all documents");
    unprocessedDocuments.forEach(unprocessedDocUpload -> {
      synchronized (unprocessedDocUpload) {
        if (unprocessedDocUpload.getExternalStatus() != VirusCheckStatus.PROCESSING) {
          log.debug("Document [{}] already processed - skipping", unprocessedDocUpload.getDocumentId());
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
          } else if (Objects.equals(apiConfig.getDocumentStateUnsafe(), documentStatus.getState())) {
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

    var s3ObjectMetadata = remoteS3Object.getObjectMetadata();

    log.debug(
        "Copying object: [{}] from remote S3 bucket: [{}] to object: [{}] in Tenders S3 bucket: [{}]",
        documentStatus.getDocumentFile().getUrl(), apiConfig.getS3Bucket(), tendersS3ObjectKey,
        tendersS3Service.getCredentials().getBucketName());

    tendersS3Client.putObject(tendersS3Service.getCredentials().getBucketName(), tendersS3ObjectKey,
        remoteS3Object.getObjectContent(), s3ObjectMetadata);
  }

  private byte[] getFromTendersS3(final String tendersS3ObjectKey, final String documentId, final String principal)
      throws IOException {
     return IOUtils.toByteArray(getFromTendersS3Stream(tendersS3ObjectKey,documentId, principal));
  }

  private InputStream getFromTendersS3Stream(final String tendersS3ObjectKey, final String documentId, final String principal)
          throws IOException {
    try {
      S3Object tendersS3Object = tendersS3Client
              .getObject(tendersS3Service.getCredentials().getBucketName(), tendersS3ObjectKey);
      return tendersS3Object.getObjectContent();
    } catch (AmazonServiceException e) {
      var objectData = processFailedS3DocumentsStream(tendersS3ObjectKey, documentId, principal);
      if (Objects.isNull(objectData))
        throw e;
      return objectData;
    }
  }
  
  private byte[] processFailedS3Documents(final String tendersS3ObjectKey, final String documentId, final String principal)
      throws AmazonServiceException, SdkClientException, IOException {
    var failedDoc = processFailedS3DocumentsStream(tendersS3ObjectKey, documentId, principal);
    if (failedDoc != null) {
      return IOUtils.toByteArray(failedDoc);
    }
    return null;
  }

  private InputStream processFailedS3DocumentsStream(final String tendersS3ObjectKey, final String documentId, final String principal)
          throws AmazonServiceException, SdkClientException, IOException {
    // reference of doc key format - tendersS3ObjectKey();
    log.debug("Processing failed s3 documents");
    log.debug("Document Id: {}", documentId);
    var documentUploadData = documentUploadRepo.findByDocumentId(documentId);
    if (documentUploadData.isPresent()) {
      var docStatus = documentUploadData.get();
      docStatus.setExternalStatus(VirusCheckStatus.PROCESSING);
      docStatus = documentUploadRepo.save(docStatus);
      this.processDocuments(Set.of(docStatus), principal);
      var tendersS3Object = tendersS3Client
              .getObject(tendersS3Service.getCredentials().getBucketName(), tendersS3ObjectKey);
      log.debug("Document processed successfully and found in s3 bucket");
      return tendersS3Object.getObjectContent();
    }
    log.debug("Document upload data not found with id: {}", documentId);
    return null;
  }

  private String tendersS3ObjectKey(final Integer projectId, final String eventId,
      final String documentId) {
    return String.format(TENDERS_S3_OBJECT_KEY_FORMAT, projectId, eventId, documentId);
  }
  
  public List<DocumentUpload> findDocumentByEvent(ProcurementEvent event) {
    var documents = documentUploadRepo.findByProcurementEvent(event);
    return documents.stream().toList();
  }

}
