package uk.gov.crowncommercial.dts.scale.cat.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.AuditLogEntity;

import java.time.Instant;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLogEntity, Long>,
            JpaSpecificationExecutor<AuditLogEntity> {
    @Query("select auditLog from audit_log auditLog where auditLog.timestamp >= :start and auditLo.timestamp < :end")
    List<Record> findCreatedBetween(Instant start, Instant end);

    }

