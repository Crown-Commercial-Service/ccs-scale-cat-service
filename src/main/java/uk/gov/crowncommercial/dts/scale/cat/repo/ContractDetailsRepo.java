package uk.gov.crowncommercial.dts.scale.cat.repo;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ContractDetails;

/**
 *
 */
@Repository
public interface ContractDetailsRepo extends JpaRepository<ContractDetails, Integer> {
  
  Optional<ContractDetails> findByEventId(Integer eventId);

}
