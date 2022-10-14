package uk.gov.crowncommercial.dts.scale.cat.repo;

import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.DocumentUpload;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.VirusCheckStatus;

/**
 *
 */
@Repository
public interface DocumentUploadRepo extends JpaRepository<DocumentUpload, Integer> {

  Set<DocumentUpload> findByExternalStatus(VirusCheckStatus externalStatus);
  
  Optional<DocumentUpload> findByDocumentId(String documentId);
  
  Set<DocumentUpload> findByProcurementEvent(ProcurementEvent procurementEvent);

}
