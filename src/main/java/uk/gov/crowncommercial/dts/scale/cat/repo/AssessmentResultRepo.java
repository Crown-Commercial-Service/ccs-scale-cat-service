package uk.gov.crowncommercial.dts.scale.cat.repo;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.AssessmentResult;

@Repository
public interface AssessmentResultRepo extends JpaRepository<AssessmentResult, Integer> {

  Optional<AssessmentResult> findByAssessmentIdAndSupplierOrganisationId(final Integer assessmentId,
      final String supplierOrganisationId);

}
