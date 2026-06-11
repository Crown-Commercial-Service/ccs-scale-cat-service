package uk.gov.crowncommercial.dts.scale.cat.service;

import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementProject;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.EventSuppliers;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.Supplier;

import java.util.List;

public interface EventService {
    public List<Supplier> getSuppliers(ProcurementProject project, ProcurementEvent existingEvent, String eventTypeValue);

    public EventSuppliers addSuppliers(final Integer procId, final String eventId, final EventSuppliers eventSuppliers, final boolean overwrite, final String principal);
}
