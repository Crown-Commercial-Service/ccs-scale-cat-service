package uk.gov.crowncommercial.dts.scale.cat.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.AssessmentDimensionWeighting;

public interface AssessmentDimensionWeightingRepo
    extends JpaRepository<AssessmentDimensionWeighting, Integer> {

}
