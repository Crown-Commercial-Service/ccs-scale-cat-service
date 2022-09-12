package uk.gov.crowncommercial.dts.scale.cat.repo.readonly;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uk.gov.crowncommercial.dts.scale.cat.model.assessment.ValueCount;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.SupplierSubmissionData;

import java.util.List;
import java.util.Set;

/**
 *
 */
@Repository public interface SupplierSubmissionDataRepo
    extends ReadOnlyRepository<SupplierSubmissionData, Integer> {


    Set<SupplierSubmissionData> findByDimensionIdAndExtToolIdAndSupplierIdIn(@Param("dimensionId") Integer dimensionId,
                                                                 @Param("toolId") String toolId,
                                                        @Param("supplierList") List<String> supplierId);

    //@Query("select DISTINCT ssd from SupplierSubmissionData ssd where ssd.dimensionId = :dimensionId and ssd.toolId = :toolId and ssd.lotId = :lotId and ssd.supplierId in (:supplierList)")
    Set<SupplierSubmissionData> findByDimensionIdAndExtToolIdAndLotIdAndSupplierIdIn(@Param("dimensionId") Integer dimensionId,
                                                                          @Param("toolId") String toolId,
                                                                                  @Param("lotId") String lotId,
                                                                          @Param("supplierList") List<String> supplierId);

    Set<SupplierSubmissionData> findByDimensionIdAndExtToolId(@Param("dimensionId") Integer dimensionId,
                                                                          @Param("toolId") String toolId);

    Set<SupplierSubmissionData> findByDimensionIdAndExtToolIdAndLotId(@Param("dimensionId") Integer dimensionId,
                                                                                  @Param("toolId") String toolId,
                                                                                  @Param("lotId") String lotId);

    @Query (value = "select COUNT(ssd.supplierId) AS dataCount, ssd.supplierId AS dataValue " +
            " from SupplierSubmissionData ssd " +
            " where submissionTypeName = 'Sub Contractor'" +
            " and toolId  = :toolId" +
            " GROUP BY supplierId")
    List<ValueCount> getSubContractorSubmissionCount(@Param("toolId") Integer toolId);
}
