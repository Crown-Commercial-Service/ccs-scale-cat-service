package uk.gov.crowncommercial.dts.scale.cat.repo.readonly;

import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.CalculationBase;

/**
 *
 */
@Repository public interface CalculationBaseRepo
    extends ReadOnlyRepository<CalculationBase, Integer> {

    @Query("select DISTINCT cb from CalculationBase cb where cb.assessmentId = :assessmentId")
    Set<CalculationBase> findByAssessmentId(@Param("assessmentId") Integer assessmentId);

    @Query("select DISTINCT cb from CalculationBase cb where cb.dimensionId = :dimensionId and cb.supplierId in (:supplierList)")
    Set<CalculationBase> findByDimensionIdAndSupplierIdIn(@Param("dimensionId") Integer dimensionId,
        @Param("supplierList") List<String> supplierId);

    @Query("select DISTINCT cb from CalculationBase cb where cb.dimensionId = :dimensionId")
    Set<CalculationBase> findByDimensionId(@Param("dimensionId") Integer dimensionId);

}
