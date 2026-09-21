package uk.gov.crowncommercial.dts.scale.cat.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.crowncommercial.dts.scale.cat.repo.SearchFilterSessionRepository;


import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SearchSessionCleanupServiceTest {

    @Mock
    private SearchFilterSessionRepository repository;

    private SearchSessionCleanupService cleanupService;

    private static final int EXPIRATION_HOURS = 24;

    @BeforeEach
    public void setUp() {
        cleanupService = new SearchSessionCleanupService(repository, EXPIRATION_HOURS);
    }

    @Test
    public void testCleanupOldSearchSessions() {
        // Execute the cleanup job
        cleanupService.cleanupOldSearchSessions();

        // Verify the repository delete method was called with a timestamp
        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repository, times(1)).deleteByCreatedAtBefore(captor.capture());

        LocalDateTime capturedTime = captor.getValue();
        LocalDateTime now = LocalDateTime.now();

        // Assert that the timestamp passed to the database is roughly 24 hours in the past
        assertTrue(capturedTime.isBefore(now.minusHours(23)));
        assertTrue(capturedTime.isAfter(now.minusHours(25)));
    }

    @Test
    @DisplayName("Should strictly delete records older than the configured expiration hours")
    void shouldDeleteRecordsOlderThanExpirationThreshold() {
        // Given: We freeze time to an exact, deterministic point
        LocalDateTime fixedTime = LocalDateTime.of(2026, 9, 21, 12, 0, 0);
        LocalDateTime expectedExpiryThreshold = fixedTime.minusHours(EXPIRATION_HOURS);

        // We use try-with-resources to mock static LocalDateTime.now() safely
        try (MockedStatic<LocalDateTime> mockedLocalDateTime = Mockito.mockStatic(LocalDateTime.class)) {
            mockedLocalDateTime.when(LocalDateTime::now).thenReturn(fixedTime);

            // When: The scheduled job runs
            cleanupService.cleanupOldSearchSessions();

            // Then: It deletes records exactly prior to the threshold, with no flakiness
            verify(repository, times(1)).deleteByCreatedAtBefore(expectedExpiryThreshold);
        }
    }
}