package uk.gov.crowncommercial.dts.scale.cat.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.GCloudAssessmentResult;

import java.util.Set;

public interface GCloudAssessmentResultRepo extends JpaRepository<GCloudAssessmentResult, Integer> {
    Set<GCloudAssessmentResult> findByAssessmentId(final Integer assessmentId);

    void deleteAllByAssessmentId(final Integer assessmentId);
}
