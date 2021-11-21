package uk.gov.crowncommercial.dts.scale.cat.repo;

import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.OrganisationMapping;

/**
 *
 */
@Repository
public interface OrganisationMappingRepo extends JpaRepository<OrganisationMapping, Integer> {

  Set<OrganisationMapping> findByOrganisationIdIn(Set<String> organisationIds);

  /**
   * TODO: Replace with Conclave lookup for Buyer organisation
   *
   * @param externalOrganisationId
   * @return the organisation mapping for the given external org ID
   */
  Optional<OrganisationMapping> findByExternalOrganisationId(Integer externalOrganisationId);

}
