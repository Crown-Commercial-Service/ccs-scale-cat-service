package uk.gov.crowncommercial.dts.scale.cat.assessment.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.util.Pair;
import uk.gov.crowncommercial.dts.scale.cat.assessment.*;
import uk.gov.crowncommercial.dts.scale.cat.model.assessment.ValueCount;
import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.*;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.*;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;
import uk.gov.crowncommercial.dts.scale.cat.repo.readonly.SupplierSubmissionDataRepo;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static uk.gov.crowncommercial.dts.scale.cat.model.entity.Timestamps.createTimestamps;
import static uk.gov.crowncommercial.dts.scale.cat.model.entity.Timestamps.updateTimestamps;

@RequiredArgsConstructor
public class BasicAssessmentToolCalculator implements AssessmentToolCalculator {
    private final AssessmentScoreCalculator assessmentScoreCalculator;
    private final SupplierSubmissionDataRepo supplierSubmissionDataRepo;
    private final Map<String, DimensionScoreCalculator> dimensionScoreCalculators;
    private final Map<String, ExclusionPolicy> exclusionPolicies;
    private final RetryableTendersDBDelegate retryableTendersDBDelegate;

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

        calculateSupplierScoreTotals(suppliersScores, dimensionRequirementMap, assessment, principal, assessmentCalculationBase);
        dimensionScoreCalculators.values().stream().forEach(calc -> calc.postCalculate(suppliersScores, assessment, calculationBaseSet));
        return suppliersScores;
    }

    @Override
    public List<SupplierScores> calculateAndPersistSupplierScores(AssessmentEntity assessment, String principal, List<DimensionRequirement> dimensionRequirements, Set<CalculationBase> calculationBaseSet) {
        List<SupplierScores> suppliersScoresList = calculateSupplierScores(assessment, principal, dimensionRequirements, calculationBaseSet);
        updateAssessmentResult(assessment, suppliersScoresList, principal);
        return suppliersScoresList;
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
                                              Map<String, DimensionRequirement> dimensionRequirementMap,
                                              final AssessmentEntity assessment, final String principal,
                                              final Set<CalculationBase> assessmentCalculationBase) {

        List<ValueCount> subContractorCountList = supplierSubmissionDataRepo.getSubContractorSubmissionCount(assessment.getTool().getId());
        Map<String, ValueCount> subContractorCountMap = subContractorCountList.stream()
                .collect(Collectors.toMap(ValueCount::getDataValue, Function.identity()));

        Map<Integer, CalculationParams> paramMap = new HashMap<>();

        for (DimensionRequirement req : dimensionRequirementMap.values()) {
            List<CriterionDefinition> criterions =  req.getIncludedCriteria();
            CalculationParams params = new CalculationParams();
            paramMap.put(req.getDimensionId(), params);
            for(CriterionDefinition cd : criterions){
                if(cd.getCriterionId().equals("0")){
                    boolean includeSubContractor = criterions.stream().filter(c -> c.getCriterionId().equals("1")).findFirst().isPresent();
                    params.setIncludeSubContractors(includeSubContractor);
                }
            }

        }



        suppliersScores.forEach(supplierScores -> {
            String supplierId = supplierScores.getSupplier().getId();

            supplierScores.getDimensionScores().forEach(dimensionScores -> {
                CalculationParams params = paramMap.get(dimensionScores.getDimensionId());
                if(null == params){
                    params = new CalculationParams();
                    params.setIncludeSubContractors(true);
                }
                if(params.isIncludeSubContractors())
                    params.setIncludeSubContractors(subContractorCountMap.containsKey(supplierId));
                dimensionScoreCalculators.get(dimensionScores.getName()).calculateDimensionScore(suppliersScores,
                        supplierScores,
                        supplierScores.getSupplier().getId(), dimensionScores.getDimensionId(), assessment,
                        assessmentCalculationBase, params);
            });

            assessmentScoreCalculator.calculateSupplierTotalScore(supplierScores);
        });
    }


    private void updateAssessmentResult(final AssessmentEntity assessment, List<SupplierScores> scores , final String principal) {
        for(SupplierScores score: scores) {
            String supplierOrgId = score.getSupplier().getId();
            BigDecimal supplierTotal = BigDecimal.valueOf(score.getTotal());
            var assessmentResult = retryableTendersDBDelegate
                    .findByAssessmentIdAndSupplierOrganisationId(assessment.getId(), supplierOrgId)
                    .orElse(AssessmentResult.builder().assessment(assessment)
                            .supplierOrganisationId(supplierOrgId).timestamps(createTimestamps(principal)).build());
            assessmentResult.setAssessmentResultValue(supplierTotal);

            if (assessmentResult.getId() != null) {
                updateTimestamps(assessmentResult.getTimestamps(), principal);
            }

            retryableTendersDBDelegate.save(assessmentResult);
        }
    }
}
