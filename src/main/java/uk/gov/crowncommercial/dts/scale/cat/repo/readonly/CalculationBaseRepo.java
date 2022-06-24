package uk.gov.crowncommercial.dts.scale.cat.repo.readonly;

import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Repository;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.CalculationBase;

/**
 *
 */
@Repository
public interface CalculationBaseRepo extends ReadOnlyRepository<CalculationBase, Integer> {

  Set<CalculationBase> findByAssessmentId(Integer assessmentId);

  Set<CalculationBase> findByDimensionIdAndSupplierIdIn(Integer dimensionId, List<String> supplierId);

  Set<CalculationBase> findByDimensionId(Integer dimensionId);

}
