package uk.gov.crowncommercial.dts.scale.cat.assessment.impl;

import org.springframework.stereotype.Component;
import uk.gov.crowncommercial.dts.scale.cat.assessment.CalculationParams;
import uk.gov.crowncommercial.dts.scale.cat.assessment.DimensionScoreCalculator;
import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.SupplierScores;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.AssessmentEntity;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.CalculationBase;

import java.util.Collection;
import java.util.Set;

@Component("DIM_NoopCalculator")
public class NoopDimensionCalculator implements DimensionScoreCalculator {
    @Override
    public void preCalculate(Collection<SupplierScores> suppliersScores, AssessmentEntity assessment, Set<CalculationBase> assessmentCalculationBase) {

    }

    @Override
    public void calculateDimensionScore(Collection<SupplierScores> suppliersScores, SupplierScores supplierScore, String supplierId,
                                        Integer dimensionId, AssessmentEntity assessment, Set<CalculationBase> assessmentCalculationBase,
                                        final CalculationParams params ) {

    }

    @Override
    public double calculateRequirementScore(CalculationBase calcBase, Set<CalculationBase> assessmentCalculationBase) {
        return 0;
    }

    @Override
    public void postCalculate(Collection<SupplierScores> suppliersScores, AssessmentEntity assessment, Set<CalculationBase> assessmentCalculationBase) {

    }
}
