package uk.gov.crowncommercial.dts.scale.cat.repo;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.AssessmentTool;

public interface AssessmentToolRepo extends JpaRepository<AssessmentTool, Integer> {

  Optional<AssessmentTool> findByInternalName(String internalName);
}
