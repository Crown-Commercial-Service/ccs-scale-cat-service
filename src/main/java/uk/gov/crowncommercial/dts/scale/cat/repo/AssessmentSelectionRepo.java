package uk.gov.crowncommercial.dts.scale.cat.repo;

import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.AssessmentSelection;

public interface AssessmentSelectionRepo extends JpaRepository<AssessmentSelection, Integer> {

  Set<AssessmentSelection> findByDimensionIdAndRequirementTaxonRequirementId(
      final Integer dimensionId, final Integer requirementId);

}
