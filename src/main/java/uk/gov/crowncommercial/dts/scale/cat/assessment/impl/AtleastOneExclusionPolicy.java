package uk.gov.crowncommercial.dts.scale.cat.assessment.impl;

import org.springframework.stereotype.Component;
import uk.gov.crowncommercial.dts.scale.cat.assessment.ExclusionPolicy;
import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.DimensionRequirement;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.AssessmentEntity;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.CalculationBase;

import java.util.*;
import java.util.stream.Collectors;

@Component("EXCL_AtleastOneNonZero")
public class AtleastOneExclusionPolicy implements ExclusionPolicy {

    @Override
    public Set<CalculationBase> exclude(AssessmentEntity assessment, Set<CalculationBase> assessmentCalculationBase,Integer dimensionId,
                                        DimensionRequirement dimensionRequirement, Map<String, DimensionRequirement> dimensionRequirementMap) {

        // Populate a map with per-supplier per elim-dimension-requirement submission totals
        var supplierDimensionRequirementTotals = new HashMap<String, Integer>();
        assessmentCalculationBase.stream()
                .filter(cb -> cb.getDimensionId() == dimensionId)
                .forEach(cb -> {
                    supplierDimensionRequirementTotals.merge(cb.getSupplierId(),cb.getSubmissionValue() == null ?
                            0 :
                            Integer.valueOf(cb.getSubmissionValue()), Integer::sum);
                });

        // Retain only those suppliers with +0 submission values for each elim-dimension-requirement
        var retainSuppliers = supplierDimensionRequirementTotals.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .map(Map.Entry::getKey).collect(Collectors.toSet());

        return assessmentCalculationBase.stream()
                .filter(cb -> retainSuppliers.contains(cb.getSupplierId())).collect(Collectors.toSet());
    }
}
