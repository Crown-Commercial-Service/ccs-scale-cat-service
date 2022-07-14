package uk.gov.crowncommercial.dts.scale.cat.service.ca;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;
import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.DimensionScores;
import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.RequirementScore;
import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.SupplierScores;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.AssessmentEntity;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.CalculationBase;

/**
 * Pricing calculator (DA etc). Requirement scores are a simple multiplication of supplier day rate
 * by requirement quantity. Dimension scores cross reference the Location dimension selections to
 * determine the criterion to use (i.e Nat Max/Home day rate). It also requires the min/max scores
 * of the other suppliers in the dataset. Overall score
 */
@Component
public class PricingCalculator implements CalculationAdaptor {

  static final String ERR_MSG_PRICING_DIMENSION_NOT_FOUND =
      "Pricing dimension not found in supplier scores";
  static final String ERR_MSG_NO_SUPPLIER_TOTALS_MIN =
      "No supplier totals - unable to calculate min supplier total";
  static final String ERR_MSG_NO_SUPPLIER_TOTALS_MAX =
      "No supplier totals - unable to calculate max supplier total";
  static final String DIMENSION_PRICING = "Pricing";
  static final String DIMENSION_LOCATION = "Location";
  static final String RQMT_GEO_LOCATION_REMOTE = "Remote (Supplier's own site)";

  static final String SUBMISSION_TYPE_NAT_MAX_DAY_RATE = "National Max Day Rate";
  static final String SUBMISSION_TYPE_NAT_HOME_DAY_RATE = "National Home Day Rate";

  @Override
  public double calculateRequirementScore(final CalculationBase calcBase,
      final Set<CalculationBase> assessmentCalculationBase) {
    var submissionValue = calcBase.getSubmissionValue() == null ?
        0 :
        Double.parseDouble(calcBase.getSubmissionValue());

    var requirementValue =
        calcBase.getRequirementValue() == null ? 1 : calcBase.getRequirementValue().doubleValue();

    // ACs 12 & 15
    return submissionValue * requirementValue;
  }

  @Override
  public void calculateDimensionScore(final Collection<SupplierScores> suppliersScores,
      final String supplierId, final Integer dimensionId, final AssessmentEntity assessment,
      final Set<CalculationBase> assessmentCalculationBase) {

    // TODO: Improve / get from supplierScores
    var remoteWorkingSelected = assessmentCalculationBase.stream()
        .anyMatch(cb -> RQMT_GEO_LOCATION_REMOTE.equals(cb.getRequirementName()));

    var specificLocationSelected = assessmentCalculationBase.stream()
        .anyMatch(cb -> DIMENSION_LOCATION.equals(cb.getDimensionName())
            && !RQMT_GEO_LOCATION_REMOTE.equals(cb.getRequirementName()));

    if (specificLocationSelected && !remoteWorkingSelected) {
      // ACs 12 - 14: Specific location(s), no remote working
      // Use National Maximum Day Rate
      calculateDimensionScoreHelper(suppliersScores, supplierId,
          Set.of(SUBMISSION_TYPE_NAT_MAX_DAY_RATE));

    } else if (!specificLocationSelected && remoteWorkingSelected) {
      // ACs 15 - 17: Remote working, no Specific Location(s)
      // Use National Home Day Rate
      calculateDimensionScoreHelper(suppliersScores, supplierId,
          Set.of(SUBMISSION_TYPE_NAT_HOME_DAY_RATE));
    } else if (specificLocationSelected) {
      // ACs 18 - 19: Remote working & Specific Location(s)
      calculateDimensionScoreHelper(suppliersScores, supplierId,
          Set.of(SUBMISSION_TYPE_NAT_MAX_DAY_RATE, SUBMISSION_TYPE_NAT_HOME_DAY_RATE));
    }
  }

  private void calculateDimensionScoreHelper(final Collection<SupplierScores> suppliersScores,
      final String supplierId, final Set<String> submissionTypes) {

    var allSuppliersTotals = new HashSet<Double>();
    var currentSupplierTotal = new AtomicReference<Double>();
    var currentSupplierPricingDimensionScores = new AtomicReference<DimensionScores>();
    suppliersScores.stream().forEach(supplierScores -> {

      // Calculate the total and add to suppliersNatMaxDayRateTotals
      var pricingDimensionScores = supplierScores.getDimensionScores().stream()
          .filter(ds -> DIMENSION_PRICING.equals(ds.getName())).findFirst()
          .orElseThrow(() -> new CAException(ERR_MSG_PRICING_DIMENSION_NOT_FOUND));

      var supplierTotal = pricingDimensionScores.getRequirementScores().stream()
          .filter(rs -> submissionTypes.contains(rs.getCriterion())).map(RequirementScore::getScore)
          .reduce(0d, Double::sum);
      allSuppliersTotals.add(supplierTotal);

      if (Objects.equals(supplierScores.getSupplier().getId(), supplierId)) {
        currentSupplierPricingDimensionScores.set(pricingDimensionScores);
        currentSupplierTotal.set(supplierTotal / submissionTypes.size()); // 1 (no adjustment) or 2
      }
    });

    var minSupplierTotal = allSuppliersTotals.parallelStream().min(Double::compareTo).orElseThrow(
        () -> new CAException(ERR_MSG_NO_SUPPLIER_TOTALS_MIN)) / submissionTypes.size();
    var maxSupplierTotal = allSuppliersTotals.parallelStream().max(Double::compareTo).orElseThrow(
        () -> new CAException(ERR_MSG_NO_SUPPLIER_TOTALS_MAX)) / submissionTypes.size();

    if (currentSupplierTotal.get() == null) {
      currentSupplierTotal.set(0d);
    }
    var dimensionScore = 100 - (currentSupplierTotal.get() - minSupplierTotal)
        / (maxSupplierTotal - minSupplierTotal) * 100;
    if (currentSupplierPricingDimensionScores.get() == null) {
      currentSupplierPricingDimensionScores.set(new DimensionScores().score(0d));
    }
    currentSupplierPricingDimensionScores.get().setScore(roundDouble(dimensionScore, 2));
  }

  @Override
  public void calculateSupplierTotalScore(final SupplierScores supplierScores) {
    supplierScores.setTotal(roundDouble(supplierScores.getDimensionScores().stream()
        .filter(dimensionScores -> dimensionScores.getScore()!= null)
        .map(DimensionScores::getScore).reduce(0d, Double::sum), 2));
  }

}
