package uk.gov.crowncommercial.dts.scale.cat.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig;
import uk.gov.crowncommercial.dts.scale.cat.exception.JaggaerApplicationException;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.BatchingQueueEntity;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.*;
import uk.gov.crowncommercial.dts.scale.cat.repo.BatchingQueueRepo;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static uk.gov.crowncommercial.dts.scale.cat.config.JaggaerAPIConfig.ENDPOINT;

/**
 * Jaegger Batching service layer - for interacting with the batch queue
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BatchingService {
//    @Autowired
//    BatchingQueueRepo batchingQueueRepo;

    @Value("${batching.processLimit}")
    int queueProcessLimit;

    private final JaggaerAPIConfig jaggaerAPIConfig;
    private final BatchingQueueRepo batchingQueueRepo;
    private final WebClient jaggaerWebClient;
    private final ObjectMapper objectMapper;
    private final Map<String, Class<?>> endpointToRequestClassMap;

    @PostConstruct
    private void initialiseMapping() {
        ((Map<String, Class<?>>) endpointToRequestClassMap).putAll(initialiseEndpointMapping());
    }

    /**
     * Get the contents of the batch request queue
     */
    public List<BatchingQueueEntity> getBatchQueueContents() {
        List<BatchingQueueEntity> model = new ArrayList<>();

        try {
            // Grab the contents of the queue directly from the repository, limited to our configured process cap
            Page<BatchingQueueEntity> limitedQueue = batchingQueueRepo.findAll(PageRequest.of(0, queueProcessLimit));

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
            batchingQueueRepo.deleteAllById(requestIds);
            log.info("Successfully deleted {} requests from batch queue", requestIds.size());
        } catch (Exception ex) {
            log.error("Error deleting processed requests from batch queue", ex);
            throw ex; // Re-throw to let the scheduler handle the error
        }
    }

    /**
     * Initialise the mapping of endpoints to their request classes
     */
    private Map<String, Class<?>> initialiseEndpointMapping() {
        Map<String, Class<?>> mapping = new HashMap<>();

        // Map endpoints to their request classes
        // TODO: How to handle document upload request to Jaggaer or exclude it from batching job
        mapping.put(jaggaerAPIConfig.getCreateProject().get(ENDPOINT), CreateUpdateProject.class);
        mapping.put(jaggaerAPIConfig.getCreateRfx().get(ENDPOINT), CreateUpdateRfx.class);
        mapping.put(jaggaerAPIConfig.getCreateUpdateCompany().get(ENDPOINT), CreateUpdateCompanyRequest.class);
        mapping.put(jaggaerAPIConfig.getPublishRfx().get(ENDPOINT), PublishRfx.class);
        mapping.put(jaggaerAPIConfig.getStartEvaluation().get(ENDPOINT), RfxWorkflowRequest.class);
        mapping.put(jaggaerAPIConfig.getInvalidateEvent().get(ENDPOINT), InvalidateEventRequest.class);
        mapping.put(jaggaerAPIConfig.getAward().get(ENDPOINT), RfxWorkflowRequest.class);
        mapping.put(jaggaerAPIConfig.getPreAward().get(ENDPOINT), RfxWorkflowRequest.class);
        mapping.put(jaggaerAPIConfig.getCompleteTechnical().get(ENDPOINT), RfxWorkflowRequest.class);
        mapping.put(jaggaerAPIConfig.getOpenEnvelope().get(ENDPOINT), OpenEnvelopeWorkFlowRequest.class);
        mapping.put(jaggaerAPIConfig.getCreateReplyMessage().get(ENDPOINT), CreateReplyMessage.class);
        mapping.put(jaggaerAPIConfig.getCreatUpdateScores().get(ENDPOINT), ScoringRequest.class);

        return mapping;
    }

    /**
     * Method to process a queued request
     * Uses the endpoint URL from request_url field
     */
    public void processQueuedRequest(BatchingQueueEntity queueEntity) {
        log.info("Processing queued request ID: {}, URL: {}",
                queueEntity.getRequestId(), queueEntity.getRequestUrl());

        // Update status to PROCESSING and increment attempt count
        queueEntity.setRequest_status("PROCESSING");
        queueEntity.setRequest_attempts(queueEntity.getRequest_attempts() + 1);
        queueEntity.setUpdatedAt(Instant.now());
        batchingQueueRepo.save(queueEntity);

        try {
            // Parse the payload to the request object
            Object requestPayload = parseRequestPayload(queueEntity.getRequestPayload(), queueEntity.getRequestUrl());

            // Execute the request using the endpoint URL
            String response = executeRequest(queueEntity.getRequestUrl(), requestPayload);

            // Validate the response
            validateJaggaerResponse(response);

            // Mark as completed
            queueEntity.setRequest_status("COMPLETED");
            queueEntity.setRequest_status_message("Successfully processed at " + Instant.now());
            queueEntity.setUpdatedAt(Instant.now());
            batchingQueueRepo.save(queueEntity);

            log.info("Successfully processed request ID: {}", queueEntity.getRequestId());

        } catch (Exception e) {
            log.error("Error processing request ID: {}, Error: {}", queueEntity.getRequestId(), e.getMessage(), e);

            // Mark as failed
            queueEntity.setRequest_status("FAILED");
            queueEntity.setRequest_status_message("Failed: " + e.getMessage());
            queueEntity.setUpdatedAt(Instant.now());
            batchingQueueRepo.save(queueEntity);

            throw e;
        }
    }

    /**
     * Parse the JSON payload to the appropriate request object based on endpoint URL
     */
    private Object parseRequestPayload(String jsonPayload, String endpointUrl) {
        try {
            Class<?> requestClass = endpointToRequestClassMap.get(endpointUrl);
            if (requestClass != null) {
                // Handle CreateRfx endpoint which can have different request types
                if (endpointUrl.equals(jaggaerAPIConfig.getCreateRfx().get(ENDPOINT))) {
                    // Check if it's an ExtendEventRfx by looking at the JSON structure
                    JsonNode jsonNode = objectMapper.readTree(jsonPayload);
                    if (jsonNode.has("extendData") || jsonNode.toString().contains("ExtendEventRfx")) {
                        return objectMapper.readValue(jsonPayload, ExtendEventRfx.class);
                    }
                    // Otherwise it's a CreateUpdateRfx
                    return objectMapper.readValue(jsonPayload, CreateUpdateRfx.class);
                }

                return objectMapper.readValue(jsonPayload, requestClass);
            } else {
                // Fallback for unknown endpoints
                log.warn("Unknown endpoint URL: {}, using generic JsonNode", endpointUrl);
                return objectMapper.readTree(jsonPayload);
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse request payload for endpoint: " + endpointUrl, e);
        }
    }

    /**
     * Execute the batched request
     */
    private String executeRequest(String endpoint, Object requestPayload) {
        log.debug("Executing request to endpoint: {}", endpoint);

        return jaggaerWebClient
                .post()
                .uri(endpoint)
                .bodyValue(requestPayload)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(jaggaerAPIConfig.getTimeoutDuration()))
                .block();
    }

    /**
     * Validate Jaggaer API response
     */
    private void validateJaggaerResponse(String response) throws JaggaerApplicationException {
        try {
            JsonNode responseNode = objectMapper.readTree(response);

            if (responseNode.has("returnCode") && responseNode.has("returnMessage")) {
                int returnCode = responseNode.get("returnCode").asInt();
                String returnMessage = responseNode.get("returnMessage").asText();

                if (returnCode != 200 || !"OK".equals(returnMessage)) {
                    throw new JaggaerApplicationException(returnCode, returnMessage);
                }
            }

        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse response JSON", e);
        }
    }

}