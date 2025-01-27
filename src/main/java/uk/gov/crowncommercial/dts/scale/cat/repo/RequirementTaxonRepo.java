package uk.gov.crowncommercial.dts.scale.cat.repo;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.RequirementTaxon;

public interface RequirementTaxonRepo extends JpaRepository<RequirementTaxon, Integer> {

  Optional<RequirementTaxon> findByRequirementIdAndTaxonSubmissionGroupAssessmentToolsId(final Integer requirementId,
                                                                                    final Integer toolId);
}
