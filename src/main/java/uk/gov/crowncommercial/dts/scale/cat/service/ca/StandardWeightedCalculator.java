package uk.gov.crowncommercial.dts.scale.cat.service.ca;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;
import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.DimensionScores;
import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.RequirementScore;
import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.SupplierScores;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.AssessmentEntity;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.CalculationBase;

/**
 * Standard weighting-based calculator (e.g. CA). Uses percentage weighting values provided by buyer
 * to calculate requirement and dimension scores.
 */
@Component
public class StandardWeightedCalculator implements CalculationAdaptor {

  static final String ERR_MSG_CURRENT_SUPPLIER_NOT_FOUND =
      "No current supplier ID match - unable to calculate dimension score";
  static final String ERR_MSG_DIMENSION_NOT_FOUND =
      "No current dimension ID match - unable to calculate dimension score";
  static final String SUBMISSION_TYPE_SUPPLIER = "Supplier";
  static final String SUBMISSION_TYPE_SUBCONTRACTOR = "Sub Contractor";

  @Override
  public double calculateRequirementScore(final CalculationBase calcBase,
      final Set<CalculationBase> assessmentCalculationBase) {
    // Compute the score for the row
    var numericSubmissionValue = Double.parseDouble(calcBase.getSubmissionValue());

    return numericSubmissionValue == 0 ? 0
        : numericSubmissionValue / (double) calcBase.getDimensionDivisor()
            * calcBase.getAssessmentSelectionWeightPercentage().doubleValue()
            * calcBase.getAssessmentDimensionWeightPercentage().doubleValue() / 100;
  }

  @Override
  public void calculateDimensionScore(final Collection<SupplierScores> suppliersScores,
      final String supplierId, final Integer dimensionId, final AssessmentEntity assessment,
      final Set<CalculationBase> assessmentCalculationBase) {

    // New agreed approach via Assess dim weighting / tool submission type join
    var subcontractorsAccepted = assessment.getDimensionWeightings().stream()
        .flatMap(adw -> adw.getDimensionSubmissionTypes().stream())
        .anyMatch(atst -> SUBMISSION_TYPE_SUBCONTRACTOR.equals(atst.getSubmissionType().getName()));

    var dimensionScores = suppliersScores.stream()
        .filter(ss -> Objects.equals(ss.getSupplier(), supplierId)).findFirst()
        .orElseThrow(() -> new CAException(ERR_MSG_CURRENT_SUPPLIER_NOT_FOUND)).getDimensionScores()
        .stream().filter(ds -> Objects.equals(ds.getDimensionId(), dimensionId)).findFirst()
        .orElseThrow(() -> new CAException(ERR_MSG_DIMENSION_NOT_FOUND));

    var supplierDimensionScore = dimensionScores.getRequirementScores().stream()
        .filter(rs -> SUBMISSION_TYPE_SUPPLIER.equals(rs.getCriterion()))
        .map(RequirementScore::getScore).reduce(0d, Double::sum);

    var subcontractorDimensionScore = dimensionScores.getRequirementScores().stream()
        .filter(rs -> SUBMISSION_TYPE_SUBCONTRACTOR.equals(rs.getCriterion()))
        .map(RequirementScore::getScore).reduce(0d, Double::sum);

    dimensionScores.setScore(roundDouble(
        subcontractorsAccepted ? (supplierDimensionScore + subcontractorDimensionScore) / 2
            : supplierDimensionScore,
        2));
  }

  @Override
  public void calculateSupplierTotalScore(final SupplierScores supplierScores) {
    supplierScores.setTotal(roundDouble(supplierScores.getDimensionScores().stream()
        .map(DimensionScores::getScore).reduce(0d, Double::sum), 2));
  }

}
