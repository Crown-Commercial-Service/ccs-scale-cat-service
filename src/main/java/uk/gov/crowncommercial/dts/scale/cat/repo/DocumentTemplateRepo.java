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
}
