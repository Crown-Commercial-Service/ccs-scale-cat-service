package uk.gov.crowncommercial.dts.scale.cat.assessment.impl;

import org.springframework.stereotype.Component;
import uk.gov.crowncommercial.dts.scale.cat.assessment.AssessmentScoreCalculator;
import uk.gov.crowncommercial.dts.scale.cat.assessment.DimensionScoreCalculator;
import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.DimensionScores;
import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.RequirementScore;
import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.SupplierScores;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.AssessmentEntity;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.CalculationBase;
import uk.gov.crowncommercial.dts.scale.cat.service.ca.CAException;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;

/**
 * Standard weighting-based calculator (e.g. CA). Uses percentage weighting values provided by buyer
 * to calculate requirement and dimension scores.
 */
@Component("ASMT_StandardWeighted")
public class StandardWeightedAssessmentCalculator implements AssessmentScoreCalculator {

    static final String ERR_MSG_CURRENT_SUPPLIER_NOT_FOUND =
            "No current supplier ID match - unable to calculate dimension score";
    static final String ERR_MSG_DIMENSION_NOT_FOUND =
            "No current dimension ID match - unable to calculate dimension score";
    static final String SUBMISSION_TYPE_SUPPLIER = "Supplier";
    static final String SUBMISSION_TYPE_SUBCONTRACTOR = "Sub Contractor";


    @Override
    public void calculateSupplierTotalScore(SupplierScores supplierScores) {
        supplierScores.setTotal(roundDouble(supplierScores.getDimensionScores().stream()
                .map(DimensionScores::getScore).reduce(0d, Double::sum), 2));

    }
}
