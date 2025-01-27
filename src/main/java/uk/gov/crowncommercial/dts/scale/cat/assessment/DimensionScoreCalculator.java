package uk.gov.crowncommercial.dts.scale.cat.assessment;

import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.SupplierScores;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.AssessmentEntity;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.CalculationBase;

import java.util.Collection;
import java.util.Set;

public interface DimensionScoreCalculator extends BaseCalculator {

    void preCalculate(Collection<SupplierScores> suppliersScores,final AssessmentEntity assessment,
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
    void calculateDimensionScore(Collection<SupplierScores> suppliersScores, SupplierScores supplierScore,
                                 final String supplierId,
                                 Integer dimensionId, final AssessmentEntity assessment,
                                 Set<CalculationBase> assessmentCalculationBase, CalculationParams params);

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


    void postCalculate(Collection<SupplierScores> suppliersScores,
                             final AssessmentEntity assessment,
                             Set<CalculationBase> assessmentCalculationBase);


    default boolean subContractorsAccepted(){
        return true;
    }
}
