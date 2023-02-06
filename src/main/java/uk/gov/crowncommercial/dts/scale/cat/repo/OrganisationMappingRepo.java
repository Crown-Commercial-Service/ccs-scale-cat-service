package uk.gov.crowncommercial.dts.scale.cat.repo;

import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.OrganisationMapping;

/**
 *
 */
@Repository
public interface OrganisationMappingRepo extends JpaRepository<OrganisationMapping, Integer> {
  @Query("select om from  OrganisationMapping om " +
          " where om.primaryInd is true and om.organisationId in (:organisationIds) " )
  Set<OrganisationMapping> findByOrganisationIdIn(@Param("organisationIds")Set<String> organisationIds);

  /**
   * TODO: Replace with Conclave lookup for Buyer organisation
   *
   * @param externalOrganisationId
   * @return the organisation mapping for the given external org ID
   */
  Optional<OrganisationMapping> findByExternalOrganisationId(Integer externalOrganisationId);


  Set<OrganisationMapping> findByExternalOrganisationIdIn(Set<Integer> organisationIds);
  /**
   * Find by Org/Conclave ID
   *
   * @param organisationId
   * @return optional org mapping
   */
  @Query("select om from  OrganisationMapping om " +
          " where om.primaryInd is true and organisationId = :organisationId ")
  Optional<OrganisationMapping> findByOrganisationId(@Param("organisationId") String organisationId);

}
