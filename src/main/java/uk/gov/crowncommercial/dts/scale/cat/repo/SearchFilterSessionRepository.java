package uk.gov.crowncommercial.dts.scale.cat.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.SearchFilterSession;

import java.time.LocalDateTime;

@Repository
public interface SearchFilterSessionRepository extends JpaRepository<SearchFilterSession, String> {

    // Delete records older than a certain date
    void deleteByCreatedAtBefore(LocalDateTime expiryDate);
}