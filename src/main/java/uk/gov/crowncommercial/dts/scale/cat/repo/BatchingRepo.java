package uk.gov.crowncommercial.dts.scale.cat.repo;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.BatchedRequest;

@Repository
public interface BatchingRepo extends JpaRepository<BatchedRequest, Integer> {
    Page<BatchedRequest> findAll(Pageable pageable);
}