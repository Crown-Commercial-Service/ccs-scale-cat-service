package uk.gov.crowncommercial.dts.scale.cat.assessment.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.util.Pair;
import uk.gov.crowncommercial.dts.scale.cat.assessment.AssessmentScoreCalculator;
import uk.gov.crowncommercial.dts.scale.cat.assessment.AssessmentToolCalculator;
import uk.gov.crowncommercial.dts.scale.cat.assessment.DimensionScoreCalculator;
import uk.gov.crowncommercial.dts.scale.cat.assessment.ExclusionPolicy;
import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.*;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.AssessmentEntity;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.AssessmentToolDimension;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.CalculationBase;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class BasicAssessmentToolCalculator implements AssessmentToolCalculator {
    private final AssessmentScoreCalculator assessmentScoreCalculator;
    private final Map<String, DimensionScoreCalculator> dimensionScoreCalculators;
    private final Map<String, ExclusionPolicy> exclusionPolicies;


    public List<SupplierScores> calculateSupplierScores(final AssessmentEntity assessment,
                                                        final String principal, List<DimensionRequirement> dimensionRequirements, Set<CalculationBase> calculationBaseSet) {

        Map<String, DimensionRequirement> dimensionRequirementMap = dimensionRequirements.stream().collect(Collectors.toMap(DimensionRequirement::getName, e -> e));

        final Set<CalculationBase> assessmentCalculationBase = eliminateZeroScoreSuppliers(
                assessment, dimensionRequirementMap,
                calculationBaseSet);

        var supplierScoresMap = new HashMap<String, SupplierScores>();
        var dimensionScoresMap = new HashMap<Pair<String, Integer>, DimensionScores>();

        assessmentCalculationBase.stream().forEach(calcBase -> {

            // Create (if necessary) a new map entry keyed on the supplier ID
            var supplierScores = supplierScoresMap.computeIfAbsent(calcBase.getSupplierId(),
                    supplierId -> new SupplierScores().supplier(new Supplier().id(supplierId)));

            // Compute the score for the row (requirement)
            var score = dimensionScoreCalculators.get(calcBase.getDimensionName())
                    .calculateRequirementScore(calcBase,  assessmentCalculationBase);

            // Generate an immutable map key of the supplier ID and dimension ID
            var supplierDimensionIdKeyPair = Pair.of(calcBase.getSupplierId(), calcBase.getDimensionId());
            var dimensionScoresExists = dimensionScoresMap.containsKey(supplierDimensionIdKeyPair);

            var dimensionScores = dimensionScoresMap.computeIfAbsent(supplierDimensionIdKeyPair,
                    p -> new DimensionScores().dimensionId(p.getSecond()).name(calcBase.getDimensionName()));

            var requirementScore = new RequirementScore().name(calcBase.getRequirementName())
                    .criterion(calcBase.getSubmissionTypeName()).value(calcBase.getSubmissionValue() == null ?
                            0 :
                            Integer.valueOf(calcBase.getSubmissionValue())).score((score == 0.0d) ? 0.0 : score);
            dimensionScores.addRequirementScoresItem(requirementScore);

            if (!dimensionScoresExists) {
                supplierScores.addDimensionScoresItem(dimensionScores);
            }
        });


        // Calculate the overall score per supplier from the dimension requirement weighted scores
        var suppliersScores = supplierScoresMap.values().stream().collect(Collectors.toList());
        dimensionScoreCalculators.values().stream().forEach(calc -> calc.preCalculate(suppliersScores, assessment, calculationBaseSet));

        calculateSupplierScoreTotals(suppliersScores, assessment, principal, assessmentCalculationBase);
        dimensionScoreCalculators.values().stream().forEach(calc -> calc.postCalculate(suppliersScores, assessment, calculationBaseSet));
        return suppliersScores;
    }

    private Set<CalculationBase> eliminateZeroScoreSuppliers(AssessmentEntity assessment,
                                                            Map<String, DimensionRequirement> dimensionRequirementMap,
                                                             Set<CalculationBase> assessmentCalculationBase) {
        Set<CalculationBase> result = assessmentCalculationBase;

        List<AssessmentToolDimension> assessmentToolDimensions = assessment.getTool().getDimensionMapping();

        for(AssessmentToolDimension atd : assessmentToolDimensions){
            String dimension = atd.getDimension().getName();
            Integer dimensionId = atd.getDimension().getId();
            ExclusionPolicy exclusionPolicy = exclusionPolicies.get(dimension);
            if(null != exclusionPolicy) {
                DimensionRequirement req = dimensionRequirementMap.get(dimension);
                result = exclusionPolicy.exclude(assessment, result, dimensionId, req, dimensionRequirementMap);
            }
        }

        return result;
    }


    private void calculateSupplierScoreTotals(final List<SupplierScores> suppliersScores,
                                              final AssessmentEntity assessment, final String principal,
                                              final Set<CalculationBase> assessmentCalculationBase) {
        suppliersScores.forEach(supplierScores -> {
            supplierScores.getDimensionScores().forEach(dimensionScores -> {
                dimensionScoreCalculators.get(dimensionScores.getName()).calculateDimensionScore(suppliersScores,
                        supplierScores,
                        supplierScores.getSupplier().getId(), dimensionScores.getDimensionId(), assessment,
                        assessmentCalculationBase);
            });

            assessmentScoreCalculator.calculateSupplierTotalScore(supplierScores);
        });
    }
}
