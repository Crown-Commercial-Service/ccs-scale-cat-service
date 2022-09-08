package uk.gov.crowncommercial.dts.scale.cat.assessment.impl;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import uk.gov.crowncommercial.dts.scale.cat.assessment.CalculationParams;
import uk.gov.crowncommercial.dts.scale.cat.assessment.DimensionScoreCalculator;
import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.DimensionScores;
import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.RequirementScore;
import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.SupplierScores;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.AssessmentEntity;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.CalculationBase;
import uk.gov.crowncommercial.dts.scale.cat.service.ca.CAException;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Standard weighting-based calculator (e.g. CA). Uses percentage weighting values provided by buyer
 * to calculate requirement and dimension scores.
 */
@Component("DIM_Pricing")
@Scope("prototype")
public class PricingDimensionCalculator implements DimensionScoreCalculator {
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

  private static final Set<String> SET_NAT_MAX_DAY_RATES = Set.of(SUBMISSION_TYPE_NAT_MAX_DAY_RATE);
  private static final Set<String> SET_NAT_HOME_DAY_RATES = Set.of(SUBMISSION_TYPE_NAT_HOME_DAY_RATE);
  private static final Set<String> SET_NAT_BOTH_DAY_RATES = Set.of(SUBMISSION_TYPE_NAT_MAX_DAY_RATE, SUBMISSION_TYPE_NAT_HOME_DAY_RATE);

  Set<Double> allSuppliersTotals = new HashSet<>();
  double minSupplierTotal, maxSupplierTotal;

  @Override
  public void preCalculate(Collection<SupplierScores> suppliersScores, AssessmentEntity assessment, Set<CalculationBase> assessmentCalculationBase) {
    if(0 == allSuppliersTotals.size()) {
      Set<String> submissionTypes = getSubmissionTypes(assessmentCalculationBase);

      suppliersScores.stream().forEach(supplierScores -> {
        var pricingDimensionScores = supplierScores.getDimensionScores().stream()
                .filter(ds -> DIMENSION_PRICING.equals(ds.getName())).findFirst()
                .orElse(null);

        if(null == pricingDimensionScores)
          return;

        var supplierTotal = pricingDimensionScores.getRequirementScores().stream()
                .filter(rs -> submissionTypes.contains(rs.getCriterion())).map(RequirementScore::getScore)
                .reduce(0d, Double::sum);
        allSuppliersTotals.add(supplierTotal);
      });


      minSupplierTotal = allSuppliersTotals.parallelStream().min(Double::compareTo).orElseThrow(
              () -> new CAException(ERR_MSG_NO_SUPPLIER_TOTALS_MIN)) / submissionTypes.size();
      maxSupplierTotal = allSuppliersTotals.parallelStream().max(Double::compareTo).orElseThrow(
              () -> new CAException(ERR_MSG_NO_SUPPLIER_TOTALS_MAX)) / submissionTypes.size();
    }
  }

  private Set getSubmissionTypes(Set<CalculationBase> assessmentCalculationBase, SupplierScores supplierScores){
    return getSubmissionTypes(assessmentCalculationBase);
  }


  private Set getSubmissionTypes(Set<CalculationBase> assessmentCalculationBase){
    var remoteWorkingSelected = assessmentCalculationBase.stream()
            .anyMatch(cb -> RQMT_GEO_LOCATION_REMOTE.equals(cb.getRequirementName()));

    var specificLocationSelected = assessmentCalculationBase.stream()
            .anyMatch(cb -> DIMENSION_LOCATION.equals(cb.getDimensionName())
                    && !RQMT_GEO_LOCATION_REMOTE.equals(cb.getRequirementName()));

    if (specificLocationSelected && !remoteWorkingSelected) {
      return SET_NAT_MAX_DAY_RATES;
    } else if (!specificLocationSelected && remoteWorkingSelected) {
      return SET_NAT_HOME_DAY_RATES;
    } else if (specificLocationSelected) {
      return SET_NAT_BOTH_DAY_RATES;
    }
    // TODO  throw exception;
    return Set.of();
  }

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
  public void calculateDimensionScore(final Collection<SupplierScores> suppliersScores,SupplierScores supplierScores,
      final String supplierId, final Integer dimensionId, final AssessmentEntity assessment,
      final Set<CalculationBase> assessmentCalculationBase, final CalculationParams params ) {

    calculateDimensionScoreHelper(supplierScores, supplierId,
            getSubmissionTypes(assessmentCalculationBase, supplierScores));
  }

  @Override
  public void postCalculate(Collection<SupplierScores> suppliersScores, AssessmentEntity assessment, Set<CalculationBase> assessmentCalculationBase) {

  }


  private void calculateDimensionScoreHelper(final SupplierScores supplierScores,
                                             final String supplierId, final Set<String> submissionTypes) {

    var currentSupplierTotal = new AtomicReference<Double>();
    var currentSupplierPricingDimensionScores = new AtomicReference<DimensionScores>();

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

}
