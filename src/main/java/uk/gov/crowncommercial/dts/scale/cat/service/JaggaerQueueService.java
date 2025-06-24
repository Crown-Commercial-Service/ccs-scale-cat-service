package uk.gov.crowncommercial.dts.scale.cat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.BatchingQueueEntity;
import uk.gov.crowncommercial.dts.scale.cat.repo.BatchingQueueRepo;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class JaggaerQueueService {

    private final BatchingQueueRepo repository;
    private final ObjectMapper objectMapper;

    public void queueUpdateRfxRequest(Object jaggaerPayload, String userId, String requestUrl, Integer projectId, String eventId) {
        try {
            String payload = objectMapper.writeValueAsString(jaggaerPayload);

            BatchingQueueEntity entity = BatchingQueueEntity.builder()
                    .userId(userId)
                    .projectId(projectId)
                    .eventId(eventId)
                    .requestUrl(requestUrl)
                    .requestPayload(payload)
                    .request_status("PENDING")
                    .request_status_message("Waiting for a turn to send Jaggaer Request")
                    .request_attempts(0)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();

            repository.save(entity);
            log.info("Queued Jaggaer request to {} for event {}", requestUrl, eventId);
        } catch (Exception e) {
            log.error("Failed to serialize Jaggaer request", e);
            throw new RuntimeException("Failed to queue Jaggaer request", e);
        }
    }
}
