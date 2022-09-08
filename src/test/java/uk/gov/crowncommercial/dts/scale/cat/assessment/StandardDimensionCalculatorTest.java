package uk.gov.crowncommercial.dts.scale.cat.assessment;

import org.junit.jupiter.api.Test;
import uk.gov.crowncommercial.dts.scale.cat.assessment.impl.StandardDimensionCalculator;
import uk.gov.crowncommercial.dts.scale.cat.assessment.impl.StandardSupplierSubContractorDimensionCalculator;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.CalculationBase;

import java.math.BigDecimal;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class StandardDimensionCalculatorTest {
    private StandardDimensionCalculator calculator = new StandardDimensionCalculator();
    
    @Test
    void testCalculateRequirementScoreWithValue(){
        CalculationBase base = new CalculationBase();
        base.setSubmissionValue("2");
        base.setDimensionDivisor(4);
        base.setDimensionId(2);
        base.setAssessmentSelectionWeightPercentage(BigDecimal.valueOf(20));
        base.setAssessmentDimensionWeightPercentage(BigDecimal.valueOf(30));
        double value = calculator.calculateRequirementScore(base, new HashSet<>());
        assertEquals(3, value, 0);
    }

    @Test
    void testCalculateRequirementScoreNullValue(){
        CalculationBase base = new CalculationBase();
        base.setDimensionDivisor(4);
        base.setDimensionId(2);
        base.setAssessmentSelectionWeightPercentage(BigDecimal.valueOf(20));
        base.setAssessmentDimensionWeightPercentage(BigDecimal.valueOf(30));
        double value = calculator.calculateRequirementScore(base, new HashSet<>());
        assertEquals(0, value, 0);
    }
}
