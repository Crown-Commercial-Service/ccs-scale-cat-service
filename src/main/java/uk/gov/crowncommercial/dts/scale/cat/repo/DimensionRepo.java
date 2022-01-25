package uk.gov.crowncommercial.dts.scale.cat.repo;

import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.Dimension;

public interface DimensionRepo extends JpaRepository<Dimension, String> {

  Set<Dimension> findByAssessmentTaxonsToolId(final Integer toolId);

}
