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
                log.debug("Begin scheduled processing of {} unprocessed documents",
                        unprocessedDocuments.size());
                documentUploadService.processDocuments(unprocessedDocuments, PRINCIPAL);
            } finally {
                schedulerLock.unlock();
            }
        } else {
            log.debug("Previous task still running, skipping this execution");
        }
    }
}
// void checkDocumentStatus() {
//    var unprocessedDocuments = documentUploadRepo.findByExternalStatus(VirusCheckStatus.PROCESSING);
//    log.debug("Begin scheduled processing of {} unprocessed documents",
//        unprocessedDocuments.size());
//    documentUploadService.processDocuments(unprocessedDocuments, PRINCIPAL);
//  }


