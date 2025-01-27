package uk.gov.crowncommercial.dts.scale.cat.assessment.impl;

import org.springframework.stereotype.Component;
import uk.gov.crowncommercial.dts.scale.cat.assessment.ExclusionPolicy;
import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.DimensionRequirement;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.AssessmentEntity;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.CalculationBase;

import java.util.Map;
import java.util.Set;

@Component("EXCL_NoopPolicy")
public class NoopExclusionPolicy implements ExclusionPolicy {

    @Override
    public Set<CalculationBase> exclude(AssessmentEntity assessment, Set<CalculationBase> assessmentCalculationBase, Integer dimensionId, DimensionRequirement dimensionRequirement, Map<String, DimensionRequirement> dimensionRequirementMap) {
        return assessmentCalculationBase;
    }
}
