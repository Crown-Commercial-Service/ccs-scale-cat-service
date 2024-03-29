package uk.gov.crowncommercial.dts.scale.cat.service.ca;

import static uk.gov.crowncommercial.dts.scale.cat.model.entity.Timestamps.createTimestamps;
import static uk.gov.crowncommercial.dts.scale.cat.model.entity.Timestamps.updateTimestamps;
import java.math.BigDecimal;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import jakarta.annotation.PostConstruct;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.*;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.AssessmentEntity;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.AssessmentResult;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.CalculationBase;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;

/**
 * Component to orchestrate the assessment scoring calculation, delegating to implementations of
 * {@link CalculationAdaptor} for different requirement, dimension and overall supplier total score
 * calculations.
 */
@Service
@RequiredArgsConstructor
public class AssessmentCalculationService {

  private static final Set<String> CA_ZERO_SCORE_ELIM_DIMENSIONS = Set.of("Location");

  private final RetryableTendersDBDelegate retryableTendersDBDelegate;
  private final CalculationAdaptor standardWeightedCalculator;
  private final CalculationAdaptor pricingCalculator;
  private final Map<String, CalculationAdaptor> dimensionCalculators = new HashMap<>();

  // Map of external toolId to calculator
  private final Map<String, CalculationAdaptor> toolCalculators = new HashMap<>();

  @PostConstruct
  void init() {
    // TODO: Externalise...
    dimensionCalculators.putAll(Map.of("Resource Quantity", standardWeightedCalculator,
        "Security Clearance", standardWeightedCalculator, "Service Capability",
        standardWeightedCalculator, "Scalability", standardWeightedCalculator, "Location",
        standardWeightedCalculator, "Pricing", pricingCalculator));

    toolCalculators.putAll(Map.of("1", standardWeightedCalculator, "2", pricingCalculator));
  }

  public List<SupplierScores> calculateSupplierScores(final AssessmentEntity assessment,
      final String principal, List<DimensionRequirement> dimensionRequirements) {

    final var assessmentCalculationBase = eliminateZeroScoreSuppliers(
            dimensionRequirements,
        retryableTendersDBDelegate.findCalculationBaseByAssessmentId(assessment.getId()));

    var supplierScoresMap = new HashMap<String, SupplierScores>();
    var dimensionScoresMap = new HashMap<Pair<String, Integer>, DimensionScores>();

    assessmentCalculationBase.stream().forEach(calcBase -> {

      // Create (if necessary) a new map entry keyed on the supplier ID
      var supplierScores = supplierScoresMap.computeIfAbsent(calcBase.getSupplierId(),
          supplierId -> new SupplierScores().supplier(new Supplier().id(supplierId)));

      // Compute the score for the row (requirement)
      var score = dimensionCalculators.get(calcBase.getDimensionName())
          .calculateRequirementScore(calcBase, assessmentCalculationBase);

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
    var supplierScores = supplierScoresMap.values().stream().collect(Collectors.toList());
    calculateSupplierScoreTotals(supplierScores, assessment, principal, assessmentCalculationBase);
    return supplierScores;
  }

  private void calculateSupplierScoreTotals(final List<SupplierScores> suppliersScores,
      final AssessmentEntity assessment, final String principal,
      final Set<CalculationBase> assessmentCalculationBase) {

    suppliersScores.forEach(supplierScores -> {

      // Certain dimension calculations use data from all supplier scores (e.g. Pricing)
      supplierScores.getDimensionScores().forEach(dimensionScores -> {
        dimensionCalculators.get(dimensionScores.getName()).calculateDimensionScore(suppliersScores,
            supplierScores.getSupplier().getId(), dimensionScores.getDimensionId(), assessment,
            assessmentCalculationBase);
      });

      toolCalculators.get(assessment.getTool().getExternalToolId())
          .calculateSupplierTotalScore(supplierScores);

      updateAssessmentResult(assessment, supplierScores.getSupplier().getId(),
          BigDecimal.valueOf(supplierScores.getTotal()), principal);
    });
  }

  /*
   * Get existing assessment result and update with new score, or create a new one
   */
  private void updateAssessmentResult(final AssessmentEntity assessment, final String supplierOrgId,
      final BigDecimal supplierTotal, final String principal) {
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

  /*
   * SCAT-3396 AC1
   *
   * Suppliers must be eliminated from calc, scoring and ranking if they lack provision via any
   * submission type for any single requirement in particular dimensions (e.g. neither supplier nor
   * sub-contractor can provide services in a required geographical area)
   */
  private Set<CalculationBase> eliminateZeroScoreSuppliers(List<DimensionRequirement> dimensionRequirements,
                                                           final Set<CalculationBase> assessmentCalculationBase) {

    HashMap<String, List<Integer>>  dimensionMap = new HashMap<>();

    // Populate a map with per-supplier per elim-dimension-requirement submission totals
    var supplierDimensionRequirementTotals = new HashMap<String, Map<String, Integer>>();
    assessmentCalculationBase.stream()
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
            .filter(e -> !e.getValue().containsValue(0) &&
                    hasSameValues(dimensionMap.get(e.getKey()), dimensionRequirements))
            .map(Entry::getKey).collect(Collectors.toSet());

    return assessmentCalculationBase.stream()
            .filter(cb -> retainSuppliers.contains(cb.getSupplierId())).collect(Collectors.toSet());
  }


  private boolean hasSameValues(List<Integer> dims, List<DimensionRequirement> dimensionRequirements){
    for(DimensionRequirement requirement : dimensionRequirements){
      int id = requirement.getDimensionId();
      if(dims.contains(id))
        continue;
      else return false;
    }
    return true;
  }

}
