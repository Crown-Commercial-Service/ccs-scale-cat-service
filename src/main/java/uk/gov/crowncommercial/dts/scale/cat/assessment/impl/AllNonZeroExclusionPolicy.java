package uk.gov.crowncommercial.dts.scale.cat.assessment.impl;

import org.springframework.stereotype.Component;
import uk.gov.crowncommercial.dts.scale.cat.assessment.ExclusionPolicy;
import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.DimensionRequirement;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.AssessmentEntity;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.CalculationBase;

import java.util.*;
import java.util.stream.Collectors;

@Component("EXCL_AllReqNonZero")
public class AllNonZeroExclusionPolicy implements ExclusionPolicy {

    @Override
    public Set<CalculationBase> exclude(AssessmentEntity assessment, Set<CalculationBase> assessmentCalculationBase,Integer dimensionId, DimensionRequirement dimensionRequirement, Map<String, DimensionRequirement> dimensionRequirementMap) {

        HashMap<String, List<Integer>>  dimensionMap = new HashMap<>();

        // Populate a map with per-supplier per elim-dimension-requirement submission totals
        var supplierDimensionRequirementTotals = new HashMap<String, Map<String, Integer>>();
        assessmentCalculationBase.stream()
                .filter(cb -> cb.getDimensionId() == dimensionId)
                .forEach(cb -> {
                    var dimRqmtKey = cb.getDimensionName() + '-' + cb.getRequirementName();

                    var dimension = cb.getDimensionId();

                    var supplierDimensionRequirementTotal = supplierDimensionRequirementTotals
                            .computeIfAbsent(cb.getSupplierId(), supplierId -> new HashMap<>());

                    var dimensions = dimensionMap
                            .computeIfAbsent(cb.getSupplierId(), supplierId -> new ArrayList<>());

                    if(!dimensions.contains(dimension)){
                        dimensions.add(dimension);
                    }

                    supplierDimensionRequirementTotal.merge(dimRqmtKey,cb.getSubmissionValue() == null ?
                            0 :
                            Integer.valueOf(cb.getSubmissionValue()), Integer::sum);
                });

        // Retain only those suppliers with +0 submission values for each elim-dimension-requirement
        var retainSuppliers = supplierDimensionRequirementTotals.entrySet().stream()
                .filter(e -> !e.getValue().containsValue(0))
                .map(Map.Entry::getKey).collect(Collectors.toSet());

        return assessmentCalculationBase.stream()
                .filter(cb -> retainSuppliers.contains(cb.getSupplierId())).collect(Collectors.toSet());
    }

}
