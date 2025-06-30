package uk.gov.crowncommercial.dts.scale.cat.service;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.BatchingQueueEntity;
import uk.gov.crowncommercial.dts.scale.cat.repo.BatchingQueueRepo;
import uk.gov.crowncommercial.dts.scale.cat.scheduling.TaskSchedulingClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = {BatchingService.class, TaskSchedulingClient.class},
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        "batching.processLimit=5",
        "scheduling.batchProcessing=5000"
})
@Slf4j
public class BatchingServiceTest {

    @MockitoBean
    private BatchingQueueRepo batchingRepo;

    @MockitoBean
    private AgreementsService agreementsService;

    @MockitoBean
    private JaggaerService jaggaerService;

    @Autowired
    private BatchingService batchingService;

    @Autowired
    private TaskSchedulingClient scheduledService;

    @Test
    void testProcessJaeggerBatchQueueWithMultipleItems() throws Exception {
        // Create test data - more than 350 to test the limit
        List<BatchingQueueEntity> testRequests = new ArrayList<>();
        Instant now = Instant.now();
        for (int i = 1; i <= 400; i++) {
            BatchingQueueEntity request = new BatchingQueueEntity();
            request.setUserId("test-user-" + i);
            request.setProjectId(100 + (i % 5));
            request.setEventId("event-" + String.format("%06d", i));
            request.setRequestUrl("/api/test/endpoint");
            request.setRequestPayload("{\"testField\":\"testValue" + i + "\"}");
            request.setRequest_status("PENDING");
            request.setRequest_status_message(null);
            request.setRequest_attempts(0);
            request.setCreatedAt(now.minusSeconds(i));
            request.setUpdatedAt(now.minusSeconds(i));
            testRequests.add(request);
        }

        // Create a Page object to match your service's expectation
        Page<BatchingQueueEntity> mockPage = new PageImpl<>(testRequests.subList(0, 350)); // Simulate the limit from PageRequest

        // Mock the repository calls
        when(batchingRepo.findAll(any(PageRequest.class))).thenReturn(mockPage);
        doNothing().when(batchingRepo).deleteAllById(any(List.class));

        // Execute the scheduled method
        scheduledService.processJaeggerBatchQueue();

        // Verify the repository was called correctly
        verify(batchingRepo, times(1)).findAll(any(PageRequest.class));
        verify(batchingRepo, times(1)).deleteAllById(any(List.class));

        // Verify that exactly 350 items were processed for deletion
        ArgumentCaptor<List<Integer>> idsCaptor = ArgumentCaptor.forClass(List.class);
        verify(batchingRepo).deleteAllById(idsCaptor.capture());
        assertEquals(350, idsCaptor.getValue().size(), "Should process exactly 350 items");

        log.info("Successfully tested batch processing with {} items", idsCaptor.getValue().size());
    }

    @Test
    void testProcessJaeggerBatchQueueWithEmptyQueue() throws Exception {
        // Mock empty queue
        Page<BatchingQueueEntity> emptyPage = new PageImpl<>(new ArrayList<>());
        when(batchingRepo.findAll(any(PageRequest.class))).thenReturn(emptyPage);

        // Execute the scheduled method
        scheduledService.processJaeggerBatchQueue();

        // Verify findAll was called but deleteAllById was not
        verify(batchingRepo, times(1)).findAll(any(PageRequest.class));
        verify(batchingRepo, never()).deleteAllById(any(List.class));

        log.info("Successfully tested batch processing with empty queue");
    }

    @Test
    void testProcessJaeggerBatchQueueWithFewerThan350Items() throws Exception {
        // Create test data with fewer than 350 items
        List<BatchingQueueEntity> testRequests = new ArrayList<>();
        for (int i = 1; i <= 50; i++) {
            BatchingQueueEntity request = new BatchingQueueEntity();
            request.setRequestId(i);
            testRequests.add(request);
        }

        Page<BatchingQueueEntity> mockPage = new PageImpl<>(testRequests);
        when(batchingRepo.findAll(any(PageRequest.class))).thenReturn(mockPage);
        doNothing().when(batchingRepo).deleteAllById(any(List.class));

        // Execute the scheduled method
        scheduledService.processJaeggerBatchQueue();

        // Verify exactly 50 items were processed
        ArgumentCaptor<List<Integer>> idsCaptor = ArgumentCaptor.forClass(List.class);
        verify(batchingRepo).deleteAllById(idsCaptor.capture());
        assertEquals(50, idsCaptor.getValue().size(), "Should process all 50 available items");

        log.info("Successfully tested batch processing with {} items", idsCaptor.getValue().size());
    }
}
