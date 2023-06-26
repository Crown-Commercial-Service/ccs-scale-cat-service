package uk.gov.crowncommercial.dts.scale.cat.service.documentupload;

import javax.transaction.Transactional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.VirusCheckStatus;
import uk.gov.crowncommercial.dts.scale.cat.repo.DocumentUploadRepo;

/**
 *
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DocumentUploadScheduledTask {

  static final String PRINCIPAL = "TENDERS_API_DOC_UPLOAD_DEAMON";

  private final DocumentUploadService documentUploadService;
  private final DocumentUploadRepo documentUploadRepo;

  @Scheduled(fixedDelayString = "PT1M")
  @Transactional
  void checkDocumentStatus() {
    var unprocessedDocuments = documentUploadRepo.findByExternalStatus(VirusCheckStatus.PROCESSING);
    log.debug("Begin scheduled processing of {} unprocessed documents",
        unprocessedDocuments.size());
    documentUploadService.processDocuments(unprocessedDocuments, PRINCIPAL);
  }

}
