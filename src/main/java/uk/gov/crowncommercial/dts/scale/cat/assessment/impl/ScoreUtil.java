package uk.gov.crowncommercial.dts.scale.cat.assessment.impl;

import uk.gov.crowncommercial.dts.scale.cat.model.capability.generated.SupplierScores;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.AssessmentEntity;

public final class ScoreUtil {
    public  final static  String SUBMISSION_TYPE_SUBCONTRACTOR = "Sub Contractor";

    public static boolean isSubContractorsAccepted(AssessmentEntity assessment, SupplierScores supplierScores){
        return assessment.getDimensionWeightings().stream()
                .flatMap(adw -> adw.getDimensionSubmissionTypes().stream())
                .anyMatch(atst -> SUBMISSION_TYPE_SUBCONTRACTOR.equals(atst.getSubmissionType().getName()));
    }
}
