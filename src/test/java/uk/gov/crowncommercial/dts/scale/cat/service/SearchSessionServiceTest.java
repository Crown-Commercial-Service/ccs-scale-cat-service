package uk.gov.crowncommercial.dts.scale.cat.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.crowncommercial.dts.scale.cat.model.cas.generated.SearchSessionRead;
import uk.gov.crowncommercial.dts.scale.cat.model.cas.generated.SearchSessionWrite;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.SearchFilterSession;
import uk.gov.crowncommercial.dts.scale.cat.repo.SearchFilterSessionRepository;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SearchSessionServiceTest {

    @Mock
    private SearchFilterSessionRepository repository;

    private SearchSessionService searchSessionService;

    private static final String UUID_REGEX = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";

    @BeforeEach
    void setUp() {
        searchSessionService = new SearchSessionService(repository);
    }

    @Test
    @DisplayName("Should successfully map request, generate UUID, set current time, and save to DB")
    void shouldSaveSearchSessionFilters() {
        // Given
        SearchSessionWrite request = new SearchSessionWrite();
        request.setFilters("cloud-hosting,iaas,sc");
        request.setProjectId(123);
        request.setEventId("event-001");
        request.setCreatedBy("user@test.com");

        LocalDateTime fixedNow = LocalDateTime.of(2026, 9, 21, 10, 0, 0);

        try (MockedStatic<LocalDateTime> mockedTime = Mockito.mockStatic(LocalDateTime.class)) {
            mockedTime.when(LocalDateTime::now).thenReturn(fixedNow);

            String returnedSessionId = searchSessionService.saveSearchSessionFilters(request);

            ArgumentCaptor<SearchFilterSession> captor = ArgumentCaptor.forClass(SearchFilterSession.class);
            verify(repository, times(1)).save(captor.capture());

            SearchFilterSession savedEntity = captor.getValue();

            assertNotNull(returnedSessionId, "Session ID should not be null");
            assertTrue(returnedSessionId.matches(UUID_REGEX), "Returned ID must be a valid UUID string");
            assertEquals(returnedSessionId, savedEntity.getSessionId(), "Returned ID must match the saved entity ID");

            assertEquals("cloud-hosting,iaas,sc", savedEntity.getFilters());
            assertEquals(123, savedEntity.getProjectId());
            assertEquals("event-001", savedEntity.getEventId());
            assertEquals("user@test.com", savedEntity.getCreatedBy());

            assertEquals(fixedNow, savedEntity.getCreatedAt(), "CreatedAt timestamp must precisely match the current time at execution");
        }
    }

    @Test
    @DisplayName("Should successfully retrieve and map DB entity to OpenAPI Read DTO")
    void shouldGetSearchSessionFiltersWhenSessionExists() {
        // Given
        String targetSessionId = "123e4567-e89b-12d3-a456-426614174000";

        SearchFilterSession mockDbEntity = new SearchFilterSession();
        mockDbEntity.setSessionId(targetSessionId);
        mockDbEntity.setFilters("cloud-hosting");
        mockDbEntity.setProjectId(999);
        mockDbEntity.setEventId("event-999");
        mockDbEntity.setCreatedBy("admin@test.com");

        when(repository.findById(targetSessionId)).thenReturn(Optional.of(mockDbEntity));

        Optional<SearchSessionRead> result = searchSessionService.getSearchSessionFilters(targetSessionId);

        assertTrue(result.isPresent(), "Result should be present when DB finds the record");

        SearchSessionRead mappedResponse = result.get();
        assertEquals(targetSessionId, mappedResponse.getSessionId());
        assertEquals("cloud-hosting", mappedResponse.getFilters());
        assertEquals(999, mappedResponse.getProjectId());
        assertEquals("event-999", mappedResponse.getEventId());
        assertEquals("admin@test.com", mappedResponse.getCreatedBy());
    }

    @Test
    @DisplayName("Should return empty Optional when session ID does not exist in DB")
    void shouldReturnEmptyOptionalWhenSessionDoesNotExist() {

        String invalidSessionId = "non-existent-id";
        when(repository.findById(invalidSessionId)).thenReturn(Optional.empty());
        Optional<SearchSessionRead> result = searchSessionService.getSearchSessionFilters(invalidSessionId);
        assertTrue(result.isEmpty(), "Result should be empty when DB returns no record");
        verify(repository, times(1)).findById(invalidSessionId);
    }
}