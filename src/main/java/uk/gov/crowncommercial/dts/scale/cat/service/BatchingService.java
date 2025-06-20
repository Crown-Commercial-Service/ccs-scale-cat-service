package uk.gov.crowncommercial.dts.scale.cat.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.BatchedRequest;
import uk.gov.crowncommercial.dts.scale.cat.repo.BatchingRepo;

import java.util.ArrayList;
import java.util.List;

/**
 * Jaegger Batching service layer - for interacting with the batch queue
 */
@Service
@Slf4j
public class BatchingService {
    @Autowired
    BatchingRepo batchingRepo;

    @Value("${batching.processLimit}")
    int queueProcessLimit;

    /**
     * Get the contents of the batch request queue
     */
    public List<BatchedRequest> getBatchQueueContents() {
        List<BatchedRequest> model = new ArrayList<>();

        try {
            // Grab the contents of the queue directly from the repository, limited to our configured process cap
            Page<BatchedRequest> limitedQueue = batchingRepo.findAll(PageRequest.of(0, queueProcessLimit));

            if (limitedQueue.hasContent()) {
                model = limitedQueue.getContent();
            }
        } catch (Exception ex) {
            log.error("Error fetching contents of batching queue", ex);
        }

        return model;
    }

    /**
     * Delete processed requests from the batch queue
     */
    public void deleteProcessedRequests(List<Integer> requestIds) {
        try {
            log.info("Deleting {} processed requests from batch queue", requestIds.size());
            batchingRepo.deleteAllById(requestIds);
            log.info("Successfully deleted {} requests from batch queue", requestIds.size());
        } catch (Exception ex) {
            log.error("Error deleting processed requests from batch queue", ex);
            throw ex; // Re-throw to let the scheduler handle the error
        }
    }
}