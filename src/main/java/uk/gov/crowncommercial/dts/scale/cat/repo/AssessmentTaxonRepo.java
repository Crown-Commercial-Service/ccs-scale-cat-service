package uk.gov.crowncommercial.dts.scale.cat.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.AssessmentTaxon;

import java.util.Set;

public interface AssessmentTaxonRepo extends JpaRepository<AssessmentTaxon, Integer> {
   // @Query("select at from AssessmentTaxon at, AssessmentTaxonDimension atd where at.tool.id = :toolId and atd.dimensionId = :dimensionId and at.id = atd.assessmentTaxonId")
    public Set<AssessmentTaxon> findBySubmissionGroupAssessmentToolsIdAndDimensionsId(@Param("toolId") Integer assessmentToolId, @Param("dimensionId") Integer dimensionId);
}
