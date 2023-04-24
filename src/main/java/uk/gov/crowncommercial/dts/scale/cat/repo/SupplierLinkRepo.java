package uk.gov.crowncommercial.dts.scale.cat.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.SupplierLinkEntity;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ca.TaskEntity;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.Supplier;

import java.time.Instant;
import java.util.List;

@Repository
public interface SupplierLinkRepo extends JpaRepository<SupplierLinkEntity, Long> {

}
