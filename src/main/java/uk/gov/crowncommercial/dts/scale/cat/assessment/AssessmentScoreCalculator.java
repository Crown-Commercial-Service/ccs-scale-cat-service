package uk.gov.crowncommercial.dts.scale.cat.assessment;

import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.SupplierScores;

public interface AssessmentScoreCalculator extends BaseCalculator {
    /**
     * Calculate overall scores for all suppliers in an assessment.
     *
     * @param supplierScores
     */
    void calculateSupplierTotalScore(SupplierScores supplierScores);
}
