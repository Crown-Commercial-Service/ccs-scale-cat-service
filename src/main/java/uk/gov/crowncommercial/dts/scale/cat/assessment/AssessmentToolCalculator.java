package uk.gov.crowncommercial.dts.scale.cat.assessment;

import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.DimensionRequirement;
import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.SupplierScores;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.AssessmentEntity;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.CalculationBase;

import java.util.List;
import java.util.Set;

public interface AssessmentToolCalculator {

    List<SupplierScores> calculateSupplierScores(final AssessmentEntity assessment,
                                                 final String principal,
                                                 List<DimensionRequirement> dimensionRequirements,
                                                 Set<CalculationBase> calculationBaseSet);
}
