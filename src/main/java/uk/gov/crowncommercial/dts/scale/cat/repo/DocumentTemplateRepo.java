package uk.gov.crowncommercial.dts.scale.cat.repo;

import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.DocumentTemplate;

/**
 *
 */
@Repository
public interface DocumentTemplateRepo extends JpaRepository<DocumentTemplate, Integer> {

  Set<DocumentTemplate> findByEventType(String eventType);

  Set<DocumentTemplate> findByEventStage(String eventStage);
  
  Set<DocumentTemplate> findByEventTypeAndCommercialAgreementNumberAndLotNumber(String eventType, String commercialAgreementNumber, String lotNumber);
  
  Set<DocumentTemplate> findByEventStageAndCommercialAgreementNumber(String eventStage, String commercialAgreementNumber);
  
  Set<DocumentTemplate> findByEventTypeAndCommercialAgreementNumberAndLotNumberAndTemplateGroup(String eventType, String commercialAgreementNumber, String lotNumber, Integer templateGroup);

}
