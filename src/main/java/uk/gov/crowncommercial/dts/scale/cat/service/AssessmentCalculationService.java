package uk.gov.crowncommercial.dts.scale.cat.service;

import static uk.gov.crowncommercial.dts.scale.cat.model.entity.Timestamps.createTimestamps;
import static uk.gov.crowncommercial.dts.scale.cat.model.entity.Timestamps.updateTimestamps;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.DimensionScores;
import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.RequirementScore;
import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.SupplierScores;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.AssessmentEntity;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.AssessmentResult;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.CalculationBase;
import uk.gov.crowncommercial.dts.scale.cat.repo.RetryableTendersDBDelegate;

/**
 *
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AssessmentCalculationService {

  private static final String SUBMISSION_TYPE_SUPPLIER = "Supplier";
  private static final String SUBMISSION_TYPE_SUBCONTRACTOR = "Sub Contractor";
  private static final Set<String> CA_ZERO_SCORE_ELIM_DIMENSIONS = Set.of("Location");

  private final RetryableTendersDBDelegate retryableTendersDBDelegate;

  public List<SupplierScores> calculateSupplierScores(final AssessmentEntity assessment,
      final String principal) {

    var assessmentCalculationBase = eliminateZeroScoreSuppliers(
        retryableTendersDBDelegate.findCalculationBaseByAssessmentId(assessment.getId()));

    var supplierScoresMap = new HashMap<String, SupplierScores>();
    var dimensionScoresMap = new HashMap<Pair<String, Integer>, DimensionScores>();

    assessmentCalculationBase.stream().forEach(calcBase -> {

      // Create (if necessary) a new map entry keyed on the supplier ID
      var supplierScores = supplierScoresMap.computeIfAbsent(calcBase.getSupplierId(),
          supplierId -> new SupplierScores().supplier(supplierId));

      // Compute the score for the row
      var numericSubmissionValue = Double.parseDouble(calcBase.getSubmissionValue());

      // TODO: Adaptor plugin for different calcs
      var score = numericSubmissionValue == 0 ? 0
          : numericSubmissionValue / (double) calcBase.getDimensionDivisor()
              * calcBase.getAssessmentSelectionWeightPercentage().doubleValue()
              * calcBase.getAssessmentDimensionWeightPercentage().doubleValue() / 100;

      // Generate an immutable map key of the supplier ID and dimension ID
      var supplierDimensionIdKeyPair = Pair.of(calcBase.getSupplierId(), calcBase.getDimensionId());
      var dimensionScoresExists = dimensionScoresMap.containsKey(supplierDimensionIdKeyPair);

      var dimensionScores = dimensionScoresMap.computeIfAbsent(supplierDimensionIdKeyPair,
          p -> new DimensionScores().dimension(p.getSecond()));

      var requirementScore = new RequirementScore().requirement(calcBase.getRequirementName())
          .criterion(calcBase.getSubmissionTypeName())
          .value(Integer.valueOf(calcBase.getSubmissionValue()))
          .weightedValue((int) Math.round(score));
      dimensionScores.addRequirementScoresItem(requirementScore);

      if (!dimensionScoresExists) {
        supplierScores.addDimensionScoresItem(dimensionScores);
      }
    });

    // Calculate the overall score per supplier from the dimension requirement weighted scores
    var supplierScores = supplierScoresMap.values().stream().collect(Collectors.toList());
    calculateSupplierScoreTotals(supplierScores, assessment, principal);
    return supplierScores;
  }

  private void calculateSupplierScoreTotals(final List<SupplierScores> supplierScores,
      final AssessmentEntity assessment, final String principal) {
    supplierScores.forEach(supplierScore -> {

      // Not sure about this - relies on the client having submitted the 'correct' data
      var subcontractorsAccepted = supplierScore.getDimensionScores().stream()
          .flatMap(ds -> ds.getRequirementScores().stream())
          .anyMatch(rs -> SUBMISSION_TYPE_SUBCONTRACTOR.equals(rs.getCriterion()));

      var supplierDimensionTotalScore = new AtomicInteger(0);
      var subcontractorDimensionTotalScore = new AtomicInteger(0);

      supplierScore.getDimensionScores().forEach(dimension -> {
        supplierDimensionTotalScore.addAndGet(dimension.getRequirementScores().stream()
            .filter(rs -> SUBMISSION_TYPE_SUPPLIER.equals(rs.getCriterion()))
            .map(RequirementScore::getWeightedValue).reduce(0, Integer::sum));

        subcontractorDimensionTotalScore.addAndGet(dimension.getRequirementScores().stream()
            .filter(rs -> SUBMISSION_TYPE_SUBCONTRACTOR.equals(rs.getCriterion()))
            .map(RequirementScore::getWeightedValue).reduce(0, Integer::sum));
      });

      var supplierTotal = subcontractorsAccepted
          ? (supplierDimensionTotalScore.get() + subcontractorDimensionTotalScore.get()) / 2
          : supplierDimensionTotalScore.get();

      supplierScore.setTotal(supplierTotal);

      updateAssessmentResult(assessment, supplierScore.getSupplier(),
          BigDecimal.valueOf(supplierTotal), principal);
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
  private Set<CalculationBase> eliminateZeroScoreSuppliers(
      final Set<CalculationBase> assessmentCalculationBase) {

    // Populate a map with per-supplier per elim-dimension-requirement submission totals
    var supplierDimensionRequirementTotals = new HashMap<String, Map<String, Integer>>();
    assessmentCalculationBase.stream()
        .filter(cb -> CA_ZERO_SCORE_ELIM_DIMENSIONS.contains(cb.getDimensionName())).forEach(cb -> {
          var dimRqmtKey = cb.getDimensionName() + '-' + cb.getRequirementName();
          var supplierDimensionRequirementTotal = supplierDimensionRequirementTotals
              .computeIfAbsent(cb.getSupplierId(), supplierId -> new HashMap<>());

          supplierDimensionRequirementTotal.merge(dimRqmtKey,
              Integer.valueOf(cb.getSubmissionValue()), Integer::sum);
        });

    // Retain only those suppliers with +0 submission values for each elim-dimension-requirement
    var retainSuppliers = supplierDimensionRequirementTotals.entrySet().stream()
        .filter(e -> !e.getValue().containsValue(0)).map(Entry::getKey).collect(Collectors.toSet());

    return assessmentCalculationBase.stream()
        .filter(cb -> retainSuppliers.contains(cb.getSupplierId())).collect(Collectors.toSet());
  }

}
