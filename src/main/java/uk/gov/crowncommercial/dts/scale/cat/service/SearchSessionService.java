package uk.gov.crowncommercial.dts.scale.cat.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uk.gov.crowncommercial.dts.scale.cat.model.cas.generated.SearchSessionRead;
import uk.gov.crowncommercial.dts.scale.cat.model.cas.generated.SearchSessionWrite;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.SearchFilterSession;
import uk.gov.crowncommercial.dts.scale.cat.repo.SearchFilterSessionRepository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Search session Service API Service layer. Handles interactions with the database and other queries.
 */

@Service
@RequiredArgsConstructor
public class SearchSessionService {

    private final SearchFilterSessionRepository repository;

    /**
     * Add search session filers into the database table.
     * @param request   - to use
     * @return  {@link String}
     */
    public String saveSearchSessionFilters(SearchSessionWrite request) {

        String sessionId = UUID.randomUUID().toString();

        SearchFilterSession session = new SearchFilterSession();
        session.setSessionId(sessionId);
        session.setFilters(request.getFilters());
        session.setProjectId(request.getProjectId());
        session.setEventId(request.getEventId());
        session.setCreatedBy(request.getCreatedBy());
        session.setCreatedAt(LocalDateTime.now());

        repository.save(session);

        return sessionId;
    }

    /**
     * Retrieve search filters with sessionId
     * @param sessionId     - to use
     * @return  {@link SearchSessionRead}
     */
    public Optional<SearchSessionRead> getSearchSessionFilters(String sessionId) {

        return repository.findById(sessionId).map(session -> {
            SearchSessionRead response = new SearchSessionRead();
            response.setSessionId(session.getSessionId());
            response.setFilters(session.getFilters());
            response.setProjectId(session.getProjectId());
            response.setEventId(session.getEventId());
            response.setCreatedBy(session.getCreatedBy());

            return response;
        });
    }
}
