package uk.gov.crowncommercial.dts.scale.cat.assessment;

import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.DimensionRequirement;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.AssessmentEntity;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.CalculationBase;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface ExclusionPolicy {
    Set<CalculationBase> exclude(AssessmentEntity assessment,
                                 Set<CalculationBase> assessmentCalculationBase,
                                 Integer dimensionId,
                                 DimensionRequirement dimensionRequirement,
                                 Map<String, DimensionRequirement> dimensionRequirementMap);
}
