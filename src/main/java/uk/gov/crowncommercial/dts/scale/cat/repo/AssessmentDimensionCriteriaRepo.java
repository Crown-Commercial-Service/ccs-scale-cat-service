package uk.gov.crowncommercial.dts.scale.cat.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.AssessmentDimensionCriteria;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.AssessmentSelection;

import java.util.List;
import java.util.Set;

public interface AssessmentDimensionCriteriaRepo extends JpaRepository<AssessmentDimensionCriteria, Integer> {

    List<AssessmentDimensionCriteria> findByAssessmentIdAndDimensionId(final Integer assessmentId
                , final Integer dimensionId);


    AssessmentDimensionCriteria findByAssessmentIdAndDimensionIdAndCriterionId(final Integer assessmentId
            , final Integer dimensionId, final Integer criterionId);

    List<AssessmentDimensionCriteria> findByAssessmentId(final Integer assessmentId);
}
