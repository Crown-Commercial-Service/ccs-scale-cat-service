package uk.gov.crowncommercial.dts.scale.cat.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.RequirementTaxon;

public interface RequirementTaxonRepo extends JpaRepository<RequirementTaxon, Integer> {

  // Optional<RequirementTaxon> findByRequirementIdAndAssessmentTaxonAssessmentToolId(
  // final Integer requirementId, final Integer toolId);
}
