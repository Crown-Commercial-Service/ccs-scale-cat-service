package uk.gov.crowncommercial.dts.scale.cat.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.GCloudAssessmentEntity;

import java.util.Set;

public interface GCloudAssessmentRepo extends JpaRepository<GCloudAssessmentEntity, Integer> {

    void deleteAllById(final Integer assessmentId);
}
