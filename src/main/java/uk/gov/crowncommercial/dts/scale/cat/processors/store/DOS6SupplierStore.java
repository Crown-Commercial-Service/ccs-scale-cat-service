package uk.gov.crowncommercial.dts.scale.cat.processors.store;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uk.gov.crowncommercial.dts.scale.cat.model.entity.ProcurementEvent;
import uk.gov.crowncommercial.dts.scale.cat.model.generated.EventSuppliers;
import uk.gov.crowncommercial.dts.scale.cat.model.jaggaer.Supplier;
import uk.gov.crowncommercial.dts.scale.cat.processors.SupplierStore;

import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DOS6SupplierStore implements SupplierStore {

    private final JaggaerSupplierStore jaggaerSupplierStore;
    private final DatabaseSupplierStore databaseSupplierStore;


    private SupplierStore getSupplierStore(ProcurementEvent event) {

        Instant publishDate = event.getPublishDate();
        if (null != publishDate && publishDate.isBefore(Instant.now())) {
            log.debug("Choosing database supplier store to manage the suppliers");
            return databaseSupplierStore;
        }
        else {
            log.debug("Choosing Jaggaer supplier store to manage the suppliers");
            return jaggaerSupplierStore;
        }
    }

    @Override
    public EventSuppliers getSuppliers(ProcurementEvent event, String principal) {
        Instant publishDate = event.getPublishDate();
        if (null != publishDate && publishDate.isBefore(Instant.now())) {
            log.debug("Choosing database supplier store to retrieve the suppliers");
            EventSuppliers result = databaseSupplierStore.getSuppliers(event, principal);
            if(null != result.getSuppliers() && result.getSuppliers().size() > 0){
                return result;
            }else{
                log.debug("No suppliers found in database, retrieve from Jaggaer");
            }
        }

        log.debug("Choosing Jaggaer supplier store to retrieve the suppliers");
        return jaggaerSupplierStore.getSuppliers(event, principal);
    }

    @Override
    public EventSuppliers storeSuppliers(ProcurementEvent event, EventSuppliers eventSuppliers, String principal) {
        return getSupplierStore(event).storeSuppliers(event, eventSuppliers, principal);
    }

    @Override
    public void deleteSupplier(ProcurementEvent event, String organisationId, String principal) {
        getSupplierStore(event).deleteSupplier(event, organisationId, principal);
    }

    @Override
    public List<Supplier> getSuppliers(ProcurementEvent event) {
        return getSupplierStore(event).getSuppliers(event);
    }
}
