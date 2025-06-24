package uk.gov.crowncommercial.dts.scale.cat.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.BatchingQueueEntity;

public interface BatchingQueueRepo extends JpaRepository<BatchingQueueEntity, Integer> {
}