package uk.gov.crowncommercial.dts.scale.cat.repo;

import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.DimensionEntity;

public interface DimensionRepo extends JpaRepository<DimensionEntity, String> {

  Optional<DimensionEntity> findByName(final String name);

  Set<DimensionEntity> findByAssessmentTaxonsToolId(final Integer toolId);

}
