package uk.gov.crowncommercial.dts.scale.cat.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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

    /**
     * Get the contents of the batch request queue
     */
    public List<BatchedRequest> getBatchQueueContents() {
        List<BatchedRequest> model = new ArrayList<>();

        try {
            // Grab the contents of the queue directly from the repository
            model = batchingRepo.findAll();
        } catch (Exception ex) {
            log.error("Error fetching contents of batching queue", ex);
        }

        return model;
    }
}