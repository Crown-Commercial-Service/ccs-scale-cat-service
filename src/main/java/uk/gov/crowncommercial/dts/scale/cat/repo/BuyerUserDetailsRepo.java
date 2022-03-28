package uk.gov.crowncommercial.dts.scale.cat.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.BuyerUserDetails;

/**
 *
 */
@Repository
public interface BuyerUserDetailsRepo extends JpaRepository<BuyerUserDetails, String> {

}
