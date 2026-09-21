package uk.gov.crowncommercial.dts.scale.cat.service;

import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import uk.gov.crowncommercial.dts.scale.cat.repo.SearchFilterSessionRepository;

import java.time.LocalDateTime;

@Slf4j
@Service
public class SearchSessionCleanupService {

    private final SearchFilterSessionRepository repository;

    private final long expirationHours;

    public SearchSessionCleanupService(
            SearchFilterSessionRepository repository,
            @Value("${search.cache.expiration-hours:12}") long expirationHours) {
        this.repository = repository;
        this.expirationHours = expirationHours;
    }

    // Runs at the top of every hour, deletes records older than expirationHours hours, set expirationHours = 12
    @Scheduled(cron = "${search.cache.expiration.schedule:0 0 * * * *}")
    @Transactional
    public void cleanupOldSearchSessions() {
        LocalDateTime expiryDate = LocalDateTime.now().minusHours(expirationHours);
        repository.deleteByCreatedAtBefore(expiryDate);
    }
}