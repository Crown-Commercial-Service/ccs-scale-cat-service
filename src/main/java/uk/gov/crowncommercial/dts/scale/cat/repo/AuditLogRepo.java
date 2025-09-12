package uk.gov.crowncommercial.dts.scale.cat.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.AuditLogEntity;
import java.time.LocalDateTime;
import java.util.List;

public interface AuditLogRepo extends JpaRepository<AuditLogEntity, Long>,
            JpaSpecificationExecutor<AuditLogEntity> {

    String auditLogQuery = "select before_update, after_update, form_url, reason, updated_by from audit_log";
            //" where audit_log.timestamp >= :start and audit_log.timestamp < :end";

    @Query(auditLogQuery)
    List<AuditLogEntity> findAuditLogEntitiesBy(LocalDateTime start, LocalDateTime end);

    }

