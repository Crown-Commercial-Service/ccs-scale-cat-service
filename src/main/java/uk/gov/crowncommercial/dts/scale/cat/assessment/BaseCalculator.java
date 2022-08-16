package uk.gov.crowncommercial.dts.scale.cat.assessment;

import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.AssessmentEntity;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.CalculationBase;

import java.util.Set;

interface BaseCalculator {
    default double roundDouble(final double value, final int scale) {
        var multiplier = Math.pow(10, scale); // 1 = 10, 2 = 100 etc
        return Math.round(value * multiplier) / multiplier;
    }

    default Set<CalculationBase> exclude(AssessmentEntity assessment, final Set<CalculationBase> assessmentCalculationBase){
        return assessmentCalculationBase;
    }
}
