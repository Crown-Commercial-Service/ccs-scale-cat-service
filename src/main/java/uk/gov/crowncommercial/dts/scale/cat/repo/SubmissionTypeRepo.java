package uk.gov.crowncommercial.dts.scale.cat.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.SubmissionType;

public interface SubmissionTypeRepo extends JpaRepository<SubmissionType, Integer> {

}
