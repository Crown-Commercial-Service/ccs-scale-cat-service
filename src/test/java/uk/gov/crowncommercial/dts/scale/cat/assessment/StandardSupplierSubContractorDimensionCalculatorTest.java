package uk.gov.crowncommercial.dts.scale.cat.assessment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import uk.gov.crowncommercial.dts.scale.cat.assessment.impl.StandardSupplierSubContractorDimensionCalculator;
import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.SupplierScores;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.AssessmentEntity;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.CalculationBase;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class StandardSupplierSubContractorDimensionCalculatorTest {
    private StandardSupplierSubContractorDimensionCalculator calculator = new StandardSupplierSubContractorDimensionCalculator();

    @Test
    void testCalculateRequirementScoreWithValue(){
        CalculationBase base = new CalculationBase();
        base.setSubmissionValue("2");
        base.setDimensionDivisor(4);
        base.setDimensionId(2);
        base.setAssessmentSelectionWeightPercentage(BigDecimal.valueOf(20));
        base.setAssessmentDimensionWeightPercentage(BigDecimal.valueOf(30));
        double value = calculator.calculateRequirementScore(base, new HashSet<>());
        double d = 3;
        assertEquals(d, value, 0);
    }

    @Test
    void testCalculateRequirementScoreNullValue(){
        CalculationBase base = new CalculationBase();
        base.setDimensionDivisor(4);
        base.setDimensionId(2);
        base.setAssessmentSelectionWeightPercentage(BigDecimal.valueOf(20));
        base.setAssessmentDimensionWeightPercentage(BigDecimal.valueOf(30));
        double value = calculator.calculateRequirementScore(base, new HashSet<>());
        double d = 0;
        assertEquals(d, value, 0);
    }
}
