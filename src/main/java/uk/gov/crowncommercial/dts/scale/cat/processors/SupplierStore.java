package uk.gov.crowncommercial.dts.scale.cat.processors;

import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.EventSuppliers;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.Supplier;

import java.util.List;

public interface SupplierStore {
    public EventSuppliers getSuppliers(ProcurementEvent event, String principal);

    public EventSuppliers storeSuppliers(ProcurementEvent event, EventSuppliers eventSuppliers, String principal);

    public List<Supplier> storeSuppliers(ProcurementEvent event, List<Supplier> suppliers, boolean overWrite, String principal);

    public void deleteSupplier(ProcurementEvent event, String organisationId, String principal);

    public List<Supplier> getSuppliers(ProcurementEvent event);
}
