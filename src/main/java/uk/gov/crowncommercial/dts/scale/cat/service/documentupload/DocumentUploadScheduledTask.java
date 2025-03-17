package uk.gov.crowncommercial.dts.scale.cat.service.documentupload;

import jakarta.transaction.Transactional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.VirusCheckStatus;
import uk.gov.crowncommercial.dts.scale.cat.repo.DocumentUploadRepo;

import java.util.concurrent.locks.ReentrantLock;

/**
 *
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DocumentUploadScheduledTask {

  static final String PRINCIPAL = "TENDERS_API_DOC_UPLOAD_DEAMON";

  private final ReentrantLock schedulerLock = new ReentrantLock();
  private final DocumentUploadService documentUploadService;
  private final DocumentUploadRepo documentUploadRepo;

  @Scheduled(fixedDelayString = "PT10S")
  @Transactional
  void checkDocumentStatus() {
    if (schedulerLock.tryLock()) {
      try {
        var unprocessedDocuments = documentUploadRepo.findByExternalStatus(VirusCheckStatus.PROCESSING);
        documentUploadService.processDocuments(unprocessedDocuments, PRINCIPAL);
      } catch (Exception e) {
        log.error("Error during document processing task: {}", e.getMessage(), e);
      } finally {
        schedulerLock.unlock();
      }
    }
  }
}
