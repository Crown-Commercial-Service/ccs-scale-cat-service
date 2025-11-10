package uk.gov.crowncommercial.dts.scale.cat.repo;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.audit.AuditLog;

import java.sql.Timestamp;
import java.util.List;

@Repository
public interface AuditLogRepo extends JpaRepository<AuditLog, Integer> {

    // Get all logs ordered by timestamp DESC
    List<AuditLog> findAllByOrderByTimestampDesc();

    // Get logs between two dates ordered by timestamp DESC
    List<AuditLog> findByTimestampBetweenOrderByTimestampDesc(
            Timestamp fromDate,
            Timestamp toDate
    );
}
