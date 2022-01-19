package uk.gov.crowncommercial.dts.scale.cat.repo;

import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.AssessmentEntity;

public interface AssessmentRepo extends JpaRepository<AssessmentEntity, Integer> {

  Set<AssessmentEntity> findByCreatedBy(final String userId);

}
