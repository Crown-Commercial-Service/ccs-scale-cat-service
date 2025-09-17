package uk.gov.crowncommercial.dts.scale.cat.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.audit.AuditLog;

@Repository
public interface AuditLogRepo extends JpaRepository<AuditLog, Integer> {
}
