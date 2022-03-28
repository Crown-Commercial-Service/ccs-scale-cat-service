package uk.gov.crowncommercial.dts.scale.cat.service.ca;

import java.util.Collection;
import java.util.Set;
import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.SupplierScores;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.AssessmentEntity;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.CalculationBase;

/**
 * Capability Assessment calculation interface. Defines API for calculating supplier scores at 3
 * levels (requirement, dimension, overall)
 */
public interface CalculationAdaptor {

  /**
   * Calculates a score for an individual requirement, i.e. a single row in the calculation base
   * result set.
   *
   * @param calcBase the current requirement calculation base
   * @param assessmentCalculationBase the entire result set
   * @return a score
   */
  double calculateRequirementScore(CalculationBase calcBase,
      Set<CalculationBase> assessmentCalculationBase);

  /**
   * Calculate a score for a CA dimension, i.e across all requirements.
   *
   * @param suppliersScores container for all supplier's requirements scores. Certain
   *        tools/dimensions (e.g. Pricing) require values and aggregations from the entire dataset.
   * @param supplierId the supplier for this calculation
   * @param dimensionId the dimension ID for this calculation
   * @param assessment
   * @param assessmentCalculationBase
   */
  void calculateDimensionScore(Collection<SupplierScores> suppliersScores, String supplierId,
      Integer dimensionId, final AssessmentEntity assessment,
      Set<CalculationBase> assessmentCalculationBase);

  /**
   * Calculate overall scores for all suppliers in an assessment.
   *
   * @param supplierScores
   */
  void calculateSupplierTotalScore(SupplierScores supplierScores);

  default double roundDouble(final double value, final int scale) {
    var multiplier = Math.pow(10, scale); // 1 = 10, 2 = 100 etc
    return Math.round(value * multiplier) / multiplier;
  }

}
