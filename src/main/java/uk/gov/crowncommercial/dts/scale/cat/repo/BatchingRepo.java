package uk.gov.crowncommercial.dts.scale.cat.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.BatchedRequest;

import java.util.List;

@Repository
public interface BatchingRepo extends JpaRepository<BatchedRequest, Integer> {
    List<BatchedRequest> findAll();
}