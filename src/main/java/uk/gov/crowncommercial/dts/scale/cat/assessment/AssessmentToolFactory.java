package uk.gov.crowncommercial.dts.scale.cat.assessment;

import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.AssessmentEntity;

public interface AssessmentToolFactory{
    AssessmentToolCalculator getAssessmentTool(AssessmentEntity assessment);
}
