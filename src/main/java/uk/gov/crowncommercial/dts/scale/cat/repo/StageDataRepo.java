package uk.gov.crowncommercial.dts.scale.cat.repo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import uk.gov.crowncommercial.dts.scale.cat.model.entity.StageDataEntity;

public interface StageDataRepo extends JpaRepository<StageDataEntity, String> {
    Optional<StageDataEntity> findByEventId(String eventId);
}
