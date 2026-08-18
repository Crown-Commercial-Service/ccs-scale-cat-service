package uk.gov.crowncommercial.dts.scale.cat.service.documentupload;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import uk.gov.crowncommercial.dts.scale.cat.config.DocumentUploadAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.config.paas.AWSS3Credentials;
import uk.gov.crowncommercial.dts.scale.cat.config.paas.AWSS3Service;
import uk.gov.crowncommercial.dts.scale.cat.model.documentupload.DocumentFile;
import uk.gov.crowncommercial.dts.scale.cat.model.documentupload.DocumentStatus;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.DocumentUpload;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementProject;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.Timestamps;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.VirusCheckStatus;
import uk.gov.crowncommercial.dts.scale.cat.repo.DocumentUploadRepo;
import uk.gov.crowncommercial.dts.scale.cat.repo.ProcurementEventRepo;
import uk.gov.crowncommercial.dts.scale.cat.service.WebclientWrapper;

@ExtendWith(MockitoExtension.class)
class DocumentUploadServiceTest {

  private static final String SOURCE_BUCKET = "docup-bucket";
  private static final String SOURCE_KEY = "uploads/document/document_file/external-id/document.docx";
  private static final String TENDERS_BUCKET = "tenders-bucket";
  private static final String GET_DOCUMENT_URI = "/documents/{document-id}";
  private static final String EXTERNAL_DOCUMENT_ID = "external-id";
  private static final String DOCUMENT_ID = "document-id=";
  private static final String EXPECTED_TENDERS_KEY =
      "25843/ocds-pfhb7i-27025/" + DOCUMENT_ID;
  private static final String PRINCIPAL = "test-principal";
  private static final byte[] DOCUMENT_BYTES = "document content".getBytes();

  @Mock
  private S3Client documentUploadS3Client;

  @Mock
  private S3Client tendersS3Client;

  @Mock
  private AWSS3Service tendersS3Service;

  @Mock
  private DocumentUploadAPIConfig apiConfig;

  @Mock
  private WebClient docUploadSvcUploadWebclient;

  @Mock
  private WebClient docUploadSvcGetWebclient;

  @Mock
  private WebclientWrapper webclientWrapper;

  @Mock
  private DocumentUploadRepo documentUploadRepo;

  @Mock
  private ProcurementEventRepo procurementEventRepo;

  private DocumentUploadService documentUploadService;

  @BeforeEach
  void setUp() {
    documentUploadService = new DocumentUploadService(documentUploadS3Client, tendersS3Client,
        tendersS3Service, apiConfig, docUploadSvcUploadWebclient, docUploadSvcGetWebclient,
        webclientWrapper, documentUploadRepo, procurementEventRepo);
  }

  @Test
  void processDocumentsCopiesSafeDocumentToTendersS3WithoutLeadingSlash() {
    var documentUpload = documentUpload(VirusCheckStatus.PROCESSING);
    var documentStatus = safeDocumentStatus();

    stubConfiguration(documentStatus);
    when(documentUploadS3Client.getObject(any(GetObjectRequest.class)))
        .thenReturn(s3Object(DOCUMENT_BYTES));

    documentUploadService.processDocuments(List.of(documentUpload), PRINCIPAL);

    var sourceRequest = ArgumentCaptor.forClass(GetObjectRequest.class);
    verify(documentUploadS3Client).getObject(sourceRequest.capture());
    assertEquals(SOURCE_BUCKET, sourceRequest.getValue().bucket());
    assertEquals(SOURCE_KEY, sourceRequest.getValue().key());

    var targetRequest = ArgumentCaptor.forClass(PutObjectRequest.class);
    verify(tendersS3Client).putObject(targetRequest.capture(), any(RequestBody.class));

    assertEquals(TENDERS_BUCKET, targetRequest.getValue().bucket());
    assertEquals(EXPECTED_TENDERS_KEY, targetRequest.getValue().key());
    assertFalse(targetRequest.getValue().key().startsWith("/"));
    assertEquals((long) DOCUMENT_BYTES.length, targetRequest.getValue().contentLength());
    assertEquals(VirusCheckStatus.SAFE, documentUpload.getExternalStatus());
    verify(documentUploadRepo).save(documentUpload);
  }

  @Test
  void retrieveDocumentStreamFallsBackToLegacyLeadingSlashKey() throws IOException {
    var documentUpload = documentUpload(VirusCheckStatus.SAFE);
    stubTendersBucket();

    when(tendersS3Client.getObject(any(GetObjectRequest.class))).thenAnswer(invocation -> {
      var request = invocation.getArgument(0, GetObjectRequest.class);

      if (EXPECTED_TENDERS_KEY.equals(request.key())) {
        throw NoSuchKeyException.builder().message("missing current key").build();
      }

      if (("/" + EXPECTED_TENDERS_KEY).equals(request.key())) {
        return s3Object(DOCUMENT_BYTES);
      }

      throw new AssertionError("Unexpected S3 key: " + request.key());
    });

    try (var stream = documentUploadService.retrieveDocumentStream(documentUpload, PRINCIPAL)) {
      assertArrayEquals(DOCUMENT_BYTES, stream.readAllBytes());
    }

    var request = ArgumentCaptor.forClass(GetObjectRequest.class);
    verify(tendersS3Client, times(2)).getObject(request.capture());

    var keys = request.getAllValues().stream().map(GetObjectRequest::key).toList();
    assertEquals(List.of(EXPECTED_TENDERS_KEY, "/" + EXPECTED_TENDERS_KEY), keys);
  }

  private void stubConfiguration(final DocumentStatus documentStatus) {
    when(apiConfig.getS3Bucket()).thenReturn(SOURCE_BUCKET);
    when(apiConfig.getDocumentStateSafe()).thenReturn("safe");
    when(apiConfig.getTimeoutDuration()).thenReturn(10);
    when(apiConfig.getGetDocumentRecord())
        .thenReturn(Map.of(DocumentUploadAPIConfig.KEY_URI_TEMPLATE, GET_DOCUMENT_URI));
    when(webclientWrapper.getOptionalResource(eq(DocumentStatus.class), eq(docUploadSvcGetWebclient),
        eq(10), eq(GET_DOCUMENT_URI), eq(EXTERNAL_DOCUMENT_ID)))
        .thenReturn(Optional.of(documentStatus));
    stubTendersBucket();
  }

  private void stubTendersBucket() {
    var credentials = AWSS3Credentials.builder()
        .bucketName(TENDERS_BUCKET)
        .build();
    when(tendersS3Service.getCredentials()).thenReturn(AWSS3Service.builder()
        .credentials(credentials)
        .build()
        .getCredentials());
  }

  private DocumentUpload documentUpload(final VirusCheckStatus status) {
    return DocumentUpload.builder()
        .procurementEvent(procurementEvent())
        .documentId(DOCUMENT_ID)
        .externalDocumentId(EXTERNAL_DOCUMENT_ID)
        .externalStatus(status)
        .timestamps(Timestamps.createTimestamps(PRINCIPAL))
        .build();
  }

  private ProcurementEvent procurementEvent() {
    return ProcurementEvent.builder()
        .id(27025)
        .ocdsAuthorityName("ocds")
        .ocidPrefix("pfhb7i")
        .project(ProcurementProject.builder()
            .id(25843)
            .build())
        .build();
  }

  private DocumentStatus safeDocumentStatus() {
    return DocumentStatus.builder()
        .id(EXTERNAL_DOCUMENT_ID)
        .state("safe")
        .documentFile(DocumentFile.builder()
            .url(SOURCE_KEY)
            .build())
        .build();
  }

  private ResponseInputStream<GetObjectResponse> s3Object(final byte[] bytes) {
    var response = GetObjectResponse.builder()
        .contentLength((long) bytes.length)
        .contentType("application/octet-stream")
        .build();

    return new ResponseInputStream<>(response,
        AbortableInputStream.create(new ByteArrayInputStream(bytes)));
  }
}
