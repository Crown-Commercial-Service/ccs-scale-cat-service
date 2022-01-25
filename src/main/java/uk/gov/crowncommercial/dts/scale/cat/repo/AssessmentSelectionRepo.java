package uk.gov.crowncommercial.dts.scale.cat.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.AssessmentSelection;

public interface AssessmentSelectionRepo extends JpaRepository<AssessmentSelection, Integer> {
}
