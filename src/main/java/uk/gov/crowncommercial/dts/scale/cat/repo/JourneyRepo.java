package uk.gov.crowncommercial.dts.scale.cat.repo;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.JourneyEntity;

/**
 *
 */
@Repository
public interface JourneyRepo extends JpaRepository<JourneyEntity, Integer> {

  Optional<JourneyEntity> findByExternalId(String externalId);

}
