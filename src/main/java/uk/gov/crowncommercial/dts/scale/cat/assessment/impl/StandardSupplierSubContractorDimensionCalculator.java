package uk.gov.crowncommercial.dts.scale.cat.assessment.impl;

import org.springframework.stereotype.Component;
import uk.gov.crowncommercial.dts.scale.cat.assessment.CalculationParams;
import uk.gov.crowncommercial.dts.scale.cat.assessment.DimensionScoreCalculator;
import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.DimensionScores;
import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.RequirementScore;
import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.SupplierScores;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.AssessmentEntity;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.CalculationBase;
import uk.gov.crowncommercial.dts.scale.cat.service.ca.CAException;
import uk.gov.crowncommercial.dts.scale.cat.service.ca.CalculationAdaptor;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;

/**
 * Standard weighting-based calculator (e.g. CA). Uses percentage weighting values provided by buyer
 * to calculate requirement and dimension scores.
 */
@Component("DIM_StandardCombined")
public class StandardSupplierSubContractorDimensionCalculator extends StandardDimensionCalculator {

    static final String ERR_MSG_CURRENT_SUPPLIER_NOT_FOUND =
            "No current supplier ID match - unable to calculate dimension score";
    static final String ERR_MSG_DIMENSION_NOT_FOUND =
            "No current dimension ID match - unable to calculate dimension score";
    static final String SUBMISSION_TYPE_SUPPLIER = "Supplier";
    static final String SUBMISSION_TYPE_SUBCONTRACTOR = "Sub Contractor";

    @Override
    public void preCalculate(Collection<SupplierScores> suppliersScores, AssessmentEntity assessment, Set<CalculationBase> assessmentCalculationBase) {

    }

    @Override
    public double calculateRequirementScore(final CalculationBase calcBase,
                                            final Set<CalculationBase> assessmentCalculationBase) {
        // Compute the score for the row
        return calcBase.getSubmissionValue() == null ? 0
                : Double.parseDouble(calcBase.getSubmissionValue()) / (double) calcBase.getDimensionDivisor()
                * calcBase.getAssessmentSelectionWeightPercentage().doubleValue()
                * calcBase.getAssessmentDimensionWeightPercentage().doubleValue() / 100;
    }

    @Override
    public void calculateDimensionScore(final Collection<SupplierScores> suppliersScores, SupplierScores supplierScores,
                                        final String supplierId, final Integer dimensionId, final AssessmentEntity assessment,
                                        final Set<CalculationBase> assessmentCalculationBase,final CalculationParams params ) {

        var subcontractorsAccepted = params.isIncludeSubContractors();

        var dimensionScores = supplierScores.getDimensionScores()
                .stream().filter(ds -> Objects.equals(ds.getDimensionId(), dimensionId)).findFirst()
                .orElseThrow(() -> new CAException(ERR_MSG_DIMENSION_NOT_FOUND));

        var supplierDimensionScore = calculateScore(dimensionScores, SUBMISSION_TYPE_SUPPLIER);

        if (subcontractorsAccepted) {
            var subcontractorDimensionScore = calculateScore(dimensionScores, SUBMISSION_TYPE_SUBCONTRACTOR);
            var finalScore = roundDouble((supplierDimensionScore + subcontractorDimensionScore) / 2, 2);
            dimensionScores.setScore(finalScore);
        } else {
            dimensionScores.setScore(roundDouble(supplierDimensionScore, 2));
        }
    }

    @Override
    public void postCalculate(Collection<SupplierScores> suppliersScores, AssessmentEntity assessment, Set<CalculationBase> assessmentCalculationBase) {

    }

}
