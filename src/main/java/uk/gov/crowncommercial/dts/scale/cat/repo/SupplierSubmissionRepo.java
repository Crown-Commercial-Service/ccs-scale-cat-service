package uk.gov.crowncommercial.dts.scale.cat.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.SupplierSubmission;

import java.util.Set;

public interface SupplierSubmissionRepo extends JpaRepository<SupplierSubmission, Integer> {

    String sqlQuery =
         " select ss.supplier_submission_id from supplier_submissions ss"
        + " inner join lot_requirement_taxons lrt on ss.lot_requirement_taxon_id = lrt.lot_requirement_taxon_id"
        + " inner join requirement_taxons rt on lrt.requirement_taxon_id = rt.requirement_taxon_id"
        + " inner join assessment_taxons at2 on rt.assessment_taxon_id = at2.assessment_taxon_id"
        + " inner join assessment_taxon_dimensions atd on at2.assessment_taxon_id = atd.assessment_taxon_id"
        + " inner join dimensions d on atd.dimension_id = d.dimension_id"
        + " where at2.assessment_tool_id = :toolId"
        + " and d.dimension_id = :dimensionId";

    @Query(value = sqlQuery, nativeQuery = true)
    Set<Integer> findSupplierByToolIdAndDimensionId(final Integer toolId,
        final Integer dimensionId);

    @Query(value = sqlQuery
        + " and lrt.lot_id  = :lotId"
        , nativeQuery = true)
    Set<Integer> findSupplierByToolIdAndDimensionIdAndLotId(final Integer toolId,
        final Integer dimensionId,final Integer lotId);

}
